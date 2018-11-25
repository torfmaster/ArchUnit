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

    private HashMap<Integer, LinkedList<Integer>> b = new HashMap<>();
    private ArrayDeque<Edge<T, ATTACHMENT>> edgeStack = new ArrayDeque<>();
    private HashSet<List<Edge<T, ATTACHMENT>>> circuits = new HashSet<>();

    private AtomicInteger s = new AtomicInteger(0);


    CycleFinder(Graph<T, ATTACHMENT> graph) {
        this.nodes = graph.getNodes();
        this.outgoingEdges = graph.getOutgoingEdges();
        this.ordering = enumerateVertices(graph.getNodes());
    }

    ImmutableSet<Cycle<T, ATTACHMENT>> findCircuits() {
        int size = nodes.size();
        while (s.get() < size) {
            Optional<HashSet<T>> mininmalStronglyConnectedComponent = new ComponentFinder<>(nodes, outgoingEdges, ordering).findLeastScc(s.get());
            if (mininmalStronglyConnectedComponent.isPresent()) {
                HashSet<T> minimalComponent = mininmalStronglyConnectedComponent.get();
                Optional<Integer> min = getMinimalVertexIndex(minimalComponent);
                if (min.isPresent()) {
                    s.set(min.get());
                    for (T t : minimalComponent) {
                        blocked.put(t, false);
                        b.put(ordering.get(t), new LinkedList<Integer>());
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


    private boolean circuit(int vIndex, HashSet<T> component, Optional<Edge<T, ATTACHMENT>> edge) {
        boolean circuitFound = false;
        if (edge.isPresent()) {
            edgeStack.push(edge.get());
        }

        T v = ordering.inverse().get(vIndex);
        block(v);

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
                if (!b.containsKey(indexOfW)) {
                    LinkedList<Integer> integers = new LinkedList<>();
                    integers.add(vIndex);
                    b.put(indexOfW, integers);
                } else if (!b.get(indexOfW).contains(vIndex)) {
                    List<Integer> integers = b.get(indexOfW);
                    integers.add(vIndex);
                }
            }
        }

        if (edge.isPresent()) {
            edgeStack.pop();
        }
        return circuitFound;
    }

    private ImmutableSet<Edge<T, ATTACHMENT>> getAdjacents(final HashSet<T> component, final int v) {
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

    private void block(T key) {
        blocked.put(key, true);
    }

    private void unblock(int s) {
        blocked.put(ordering.inverse().get(s), false);
        if (b.containsKey(s)) {
            LinkedList<Integer> elements = b.get(s);
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

    private Optional<Integer> getMinimalVertexIndex(HashSet<T> minimalComponent) {
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
