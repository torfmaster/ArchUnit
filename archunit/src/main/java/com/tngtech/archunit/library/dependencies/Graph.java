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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

import java.util.HashSet;
import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

class Graph<T, ATTACHMENT> {
    @VisibleForTesting
    Set<T> getNodes() {
        return nodes;
    }

    @VisibleForTesting
    Multimap<T, Edge<T, ATTACHMENT>> getOutgoingEdges() {
        return outgoingEdges;
    }

    private final Set<T> nodes = new HashSet<>();
    private final Multimap<T, Edge<T, ATTACHMENT>> outgoingEdges = HashMultimap.create();

    void add(T node, Set<Edge<T, ATTACHMENT>> connectingEdges) {
        nodes.add(checkNotNull(node));
        for (Edge<T, ATTACHMENT> edge : connectingEdges) {
            addEdge(edge);
        }
    }

    private void addEdge(Edge<T, ATTACHMENT> edge) {
        checkArgument(nodes.contains(edge.getFrom()), "Node %s of edge %s is not part of the graph", edge.getFrom(), edge);
        checkArgument(nodes.contains(edge.getTo()), "Node %s of edge %s is not part of the graph", edge.getTo(), edge);
        outgoingEdges.put(edge.getFrom(), edge);
    }

    Set<Cycle<T, ATTACHMENT>> getCycles() {
        return new CycleFinder<>(this.getNodes(), this.getOutgoingEdges()).findCircuits();
    }

    @Override
    public String toString() {
        return "Graph{" +
                "nodes=" + nodes +
                ", edges=" + outgoingEdges.values() +
                '}';
    }
}
