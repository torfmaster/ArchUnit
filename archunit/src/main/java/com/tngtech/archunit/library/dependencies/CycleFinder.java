/*
 * Copyright 2018 TNG Technology Consulting GmbH
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

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

class CycleFinder<T, ATTACHMENT> {
    private Set<T> nodes;
    private Multimap<T, Edge<T, ATTACHMENT>> outgoingEdges;
    private ImmutableBiMap<T, Integer> ordering;
    private HashMap<T, Boolean> blocked = new HashMap<>();

    private HashMap<Integer, LinkedList<Integer>> blockedBy = new HashMap<>();
    private ArrayDeque<Edge<T, ATTACHMENT>> edgeStack = new ArrayDeque<>();
    private HashSet<List<Edge<T, ATTACHMENT>>> circuits = new HashSet<>();

    private AtomicInteger s = new AtomicInteger(0);


    CycleFinder(Graph<T, ATTACHMENT> graph) {
        this.nodes = graph.getNodes();
        this.outgoingEdges = graph.getOutgoingEdges();
        this.ordering = enumerateVertices(graph.getNodes());
    }

    ImmutableSet<Cycle<T, ATTACHMENT>> findCircuits() {
        LinkedList<ComponentFinder.Vertex<T, ATTACHMENT>> nodes2 = new LinkedList<>();
        ImmutableMap.Builder<T, ComponentFinder.Vertex<T, ATTACHMENT>> builder = new ImmutableMap.Builder<>();
        for (T node : nodes) {
            ComponentFinder.Vertex<T, ATTACHMENT> vertex = new ComponentFinder.Vertex<>(node, ordering.get(node), outgoingEdges.get(node));
            nodes2.add(vertex);
            builder.put(node, vertex);
        }
        ImmutableMap<T, ComponentFinder.Vertex<T, ATTACHMENT>> build = builder.build();


        int size = nodes.size();
        while (s.get() < size) {
            Optional<LinkedList<T>> mininmalStronglyConnectedComponent = new ComponentFinder<>(build, ordering, nodes2).findLeastScc(s.get());
            if (mininmalStronglyConnectedComponent.isPresent()) {
                LinkedList<T> minimalComponent = mininmalStronglyConnectedComponent.get();
                Optional<Integer> min = getMinimalVertexIndex(minimalComponent);
                if (min.isPresent()) {
                    s.set(min.get());
                    for (T t : minimalComponent) {
                        blocked.put(t, false);
                        blockedBy.put(ordering.get(t), new LinkedList<Integer>());
                    }
                    circuit(s.get(), minimalComponent, Optional.<Edge<T, ATTACHMENT>>absent());
                    s.addAndGet(1);
                } else {
                    throw new IllegalArgumentException("Unreachable code: Strongly connected components are always non-empty.");
                }
            } else {
                throw new IllegalArgumentException(String.format("Unreachable code: The set of edges with order >= %d mut be non-empty.", s.get()));
            }

        }

        return FluentIterable.from(circuits).transform(
                new Function<List<Edge<T, ATTACHMENT>>, Cycle<T, ATTACHMENT>>() {
                    @Override
                    public Cycle<T, ATTACHMENT> apply(List<Edge<T, ATTACHMENT>> input) {
                        return new Cycle<>(input);
                    }
                }).toSet();
    }


    private boolean circuit(int vIndex, LinkedList<T> component, Optional<Edge<T, ATTACHMENT>> edge) {
        boolean circuitFound = false;
        if (edge.isPresent()) {
            edgeStack.push(edge.get());
        }

        block(vIndex);

        for (Edge<T, ATTACHMENT> edgeFromVToW : getAdjacents(component, vIndex)) {
            T w = edgeFromVToW.getTo();
            Integer indexOfW = ordering.get(w);
            if (w.equals(ordering.inverse().get(s.get()))) {
                ImmutableList.Builder<Edge<T, ATTACHMENT>> edgeBuilder = new ImmutableList.Builder<>();
                ArrayDeque<Edge<T, ATTACHMENT>> copyOfEdgeStackToPop = edgeStack.clone();
                copyOfEdgeStackToPop.push(edgeFromVToW);
                while (!copyOfEdgeStackToPop.isEmpty()) {
                    edgeBuilder.add(copyOfEdgeStackToPop.pop());
                }
                circuits.add(edgeBuilder.build().reverse());
                circuitFound = true;
            } else if (!isBlocked(w)) {
                if (circuit(indexOfW, component, Optional.of(edgeFromVToW))) {
                    circuitFound = true;
                }
            }
        }

        if (circuitFound) {
            unblock(vIndex);
        } else {
            for (Edge<T, ATTACHMENT> w : getAdjacents(component, vIndex)) {
                Integer indexOfW = ordering.get(w.getTo());
                if (!blockedBy.containsKey(indexOfW)) {
                    LinkedList<Integer> integers = new LinkedList<>();
                    integers.add(vIndex);
                    blockedBy.put(indexOfW, integers);
                } else if (!blockedBy.get(indexOfW).contains(vIndex)) {
                    List<Integer> integers = blockedBy.get(indexOfW);
                    integers.add(vIndex);
                }
            }
        }

        if (edge.isPresent()) {
            edgeStack.pop();
        }
        return circuitFound;
    }

    private ImmutableSet<Edge<T, ATTACHMENT>> getAdjacents(final LinkedList<T> component, final int v) {
        return FluentIterable.from(
                outgoingEdges.get(ordering.inverse().get(v)))
                .filter(new Predicate<Edge<T, ATTACHMENT>>() {
                            @Override
                            public boolean apply(Edge<T, ATTACHMENT> input) {
                                return component.contains(input.getTo());
                            }
                        }
                ).toSet();

    }

    private void block(int vIndex) {
        blocked.put(ordering.inverse().get(vIndex), true);
    }

    private void unblock(int s) {
        blocked.put(ordering.inverse().get(s), false);
        if (blockedBy.containsKey(s)) {
            LinkedList<Integer> elements = blockedBy.get(s);
            List<Integer> nodesForS = ImmutableList.copyOf(elements);
            for (Integer node : nodesForS) {
                elements.remove(node);
                if (isBlocked(ordering.inverse().get(node))) {
                    unblock(node);
                }
            }
        }
    }

    private boolean isBlocked(T key) {
        return Boolean.TRUE.equals(blocked.get(key));
    }

    private ImmutableBiMap<T, Integer> enumerateVertices(Set<T> nodes) {
        ImmutableBiMap.Builder<T, Integer> builder = new ImmutableBiMap.Builder<>();
        int i = 0;
        for (T node : nodes) {
            builder.put(node, i);
            i++;
        }
        return builder.build();
    }

    private Optional<Integer> getMinimalVertexIndex(LinkedList<T> minimalComponent) {
        Optional<Integer> min = Optional.absent();
        for (T t : minimalComponent) {
            if (!min.isPresent()) {
                min = Optional.of(ordering.get(t));
            } else {
                min = Optional.of(Math.min(ordering.get(t), min.get()));
            }
        }
        return min;
    }
}
