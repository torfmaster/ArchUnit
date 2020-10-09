/*
 * Copyright 2014-2020 TNG Technology Consulting GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.tngtech.archunit.library.dependencies;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import com.google.common.base.Joiner;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import com.tngtech.archunit.library.dependencies.PrimitiveDataTypes.IntStack;

import static java.util.Arrays.binarySearch;

/**
 * Extends a {@link PrimitiveGraph} with any data structures necessary to execute Johnson's algorithm for cycle detection.
 * The idea is mainly to keep the code readable and understandable while using many primitive data structures
 * for performance reasons.
 * <br><br>
 * Note that for {@code blocked} and {@code dependentlyBlocked} more sophisticated data structures like {@link Set}
 * and {@link Multimap} seemed to be a good fit without having a big performance impact when tested locally.
 */
class JohnsonComponent {
    private final PrimitiveGraph graph;
    /**
     * We always operate on a single strongly connected component to detect cycles within.
     * Note that by convention components (represented as {@code int[]}) will always be a sequence
     * of node indexes sorted in ascending order.
     */
    private int[] stronglyConnectedComponent = new int[0];
    /**
     * Records which nodes are blocked at the moment according to Johnson's algorithm. This is mainly
     * a performance optimization so we do not have to redundantly follow paths where we know no
     * cycle can exist.
     */
    private final Set<Integer> blocked = new HashSet<>();
    /**
     * Records if we have to unblock other nodes once we unblock a specific node. Johnson's algorithm
     * uses this to free nodes if we have found a cycle to create the possibility to find a further cycle
     * through these nodes.
     */
    private final Multimap<Integer, Integer> dependentlyBlocked = HashMultimap.create();
    /**
     * Contains the nodes we have currently visited. In case we ever find a path back to the starting node,
     * we can pop this stack and consequently obtain a cycle through the starting node.
     */
    private final IntStack nodeStack;
    /**
     * Performance optimization. When we return the nodes adjacent to a specific node within this
     * strongly connected component, we initially do not know how many nodes we will return.
     * Thus we need to temporarily store these nodes in some array to copy that array with the correct
     * size in the end.
     * Note that allocating a new temporary array on each method call will make a <b>huge</b> difference
     * with regard to performance.
     */
    private final int[] tempAdjacentNodesInComponent;

    private JohnsonComponent(PrimitiveGraph graph) {
        this.graph = graph;
        nodeStack = new IntStack(graph.getSize());
        tempAdjacentNodesInComponent = new int[graph.getSize()];
        this.draw();
    }

    public void draw() {

        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("digraph G {\n");
        stringBuilder.append("node[shape=record];\n");

        for (int i=0; i<graph.getSize(); i++) {
            Collection<Integer> integers = dependentlyBlocked.get(i);
            StringBuilder blockedBuilder = new StringBuilder();
            String color = blocked.contains(i) ? "red" : "black";
            blockedBuilder.append(i+"[color="+ color +",label=\"{{"+i+"}|"+"{Dep. Blocks|");
            String blockedAsString = Joiner.on("|").join(integers);
            blockedBuilder.append(blockedAsString);
            blockedBuilder.append("}");
            blockedBuilder.append("}\"];\n");
            stringBuilder.append(blockedBuilder.toString());
        }


        for (int i=0; i<graph.getSize(); i++) {
            int[] adjacentNodesOfi = graph.getAdjacentNodesOf(i);
            for (int j=0; j<adjacentNodesOfi.length;j++) {
                stringBuilder.append(""+i+" -> " + adjacentNodesOfi[j] + ";\n");
            }
        }

        int[] stack = this.nodeStack.asArray();

        StringBuilder stackBuilder = new StringBuilder();
        stackBuilder.append("stack [label=\"{{stack}");

        for (int i=0; i<stack.length; i++) {
            stackBuilder.append("|{"+stack[i]+"}");
        }
        stackBuilder.append("}\"];\n");

        stringBuilder.append(stackBuilder.toString());

        stringBuilder.append("}");
        String text = stringBuilder.toString();
        GraphDrawer.drawGraph(text);
    }

    /**
     * Initialize the Johnson component with the strongly connected component returned by Tarjan's algorithm.
     * @param sortedStronglyConnectedComponent the array of node indexes sorted in ascending order
     */
    void init(int[] sortedStronglyConnectedComponent) {
        this.stronglyConnectedComponent = sortedStronglyConnectedComponent;
        blocked.clear();
        dependentlyBlocked.clear();
    }

    int[] getAdjacentNodesOf(int nodeIndex) {
        int index = 0;
        for (int candidate : graph.getAdjacentNodesOf(nodeIndex)) {
            if (componentContains(candidate)) {
                tempAdjacentNodesInComponent[index++] = candidate;
            }
        }
        return Arrays.copyOf(tempAdjacentNodesInComponent, index);
    }

    private boolean componentContains(int nodeIndex) {
        return binarySearch(stronglyConnectedComponent, nodeIndex) >= 0;
    }

    boolean isStartNodeIndex(int nodeIndex) {
        return getStartNodeIndex() == nodeIndex;
    }

    int getStartNodeIndex() {
        return stronglyConnectedComponent[0];
    }

    boolean isNotBlocked(int nodeIndex) {
        return !blocked.contains(nodeIndex);
    }

    void block(int nodeIndex) {
        blocked.add(nodeIndex);
        this.draw();
    }

    void unblock(int nodeIndex) {
        if (!blocked.remove(nodeIndex)) {
            return;
        }
        for (Integer dependentlyBlockedIndex : dependentlyBlocked.get(nodeIndex)) {
            unblock(dependentlyBlockedIndex);
        }
        dependentlyBlocked.get(nodeIndex).clear();
        this.draw();

    }

    /**
     * Marks node {@code indexOfNodeDependentlyBlocked} as dependently blocked by {@code indexOfNode}.
     * This means that if we ever unblock node {@code indexOfNode} we also have to unblock
     * {@code indexOfNodeDependentlyBlocked}.
     */
    void markDependentlyBlocked(int indexOfNodeDependentlyBlocked, int indexOfNode) {
        dependentlyBlocked.put(indexOfNode, indexOfNodeDependentlyBlocked);
        this.draw();

    }

    void pushOnStack(int nodeIndex) {
        nodeStack.push(nodeIndex);
        this.draw();

    }

    void popFromStack() {
        nodeStack.pop();
        this.draw();

    }

    int[] getStack() {
        return nodeStack.asArray();
    }

    static JohnsonComponent within(PrimitiveGraph graph) {
        return new JohnsonComponent(graph);
    }
}
