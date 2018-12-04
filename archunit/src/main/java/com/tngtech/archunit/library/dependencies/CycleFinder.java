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

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.collect.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

class CycleFinder<T, ATTACHMENT> {
    private Set<T> nodes;
    private Multimap<T, Edge<T, ATTACHMENT>> outgoingEdges;
    private ImmutableBiMap<T, Integer> ordering;
    private HashMap<Integer, Boolean> blocked = new HashMap<>();

    private HashMap<Integer, ArrayList<Integer>> blockedBy = new HashMap<>();
    private ArrayDeque<Edge<T, ATTACHMENT>> edgeStack = new ArrayDeque<>();
    private HashSet<List<Edge<T, ATTACHMENT>>> circuits = new HashSet<>();

    private AtomicInteger s = new AtomicInteger(0);
    private ArrayList<ComponentFinder.Vertex<T, ATTACHMENT>> substituteList;


    CycleFinder(Graph<T, ATTACHMENT> graph) {
        this.nodes = graph.getNodes();
        this.outgoingEdges = graph.getOutgoingEdges();
        this.ordering = enumerateVertices(graph.getNodes());
    }

    ImmutableSet<Cycle<T, ATTACHMENT>> findCircuits() {
        substituteList = new ArrayList<>(Collections.nCopies(nodes.size(), (ComponentFinder.Vertex<T, ATTACHMENT>) null));
        for (T node : nodes) {
            Collection<Edge<T, ATTACHMENT>> edges = outgoingEdges.get(node);
            ComponentFinder.Vertex<T, ATTACHMENT> vertex = new ComponentFinder.Vertex<>(node, ordering.get(node));
            ArrayList<Integer> outgoingEdgesArray = new ArrayList<>();
            for (Edge<T, ATTACHMENT> edge : edges) {
                outgoingEdgesArray.add(ordering.get(edge.getTo()));
            }
            vertex.outgoingEdgesArray = outgoingEdgesArray;
            vertex.outgoingEdges = new ArrayList<>(edges);
            substituteList.set(ordering.get(node), vertex);
        }


        int size = nodes.size();
        // FIXME >= s, skip direcftly

        ComponentFinder<T, ATTACHMENT> componentFinder = new ComponentFinder<>(ordering, substituteList);
        while (s.get() < size) {
            Optional<HashSet<ComponentFinder.Vertex<T, ATTACHMENT>>> mininmalStronglyConnectedComponent = componentFinder.findLeastScc(s.get());
            componentFinder.reset();
            if (mininmalStronglyConnectedComponent.isPresent()) {
                HashSet<ComponentFinder.Vertex<T, ATTACHMENT>> minimalComponent = mininmalStronglyConnectedComponent.get();
                Optional<Integer> min = getMinimalVertexIndex(minimalComponent);
                if (min.isPresent()) {
                    s.set(min.get());
                    for (ComponentFinder.Vertex<T, ATTACHMENT> t : minimalComponent) {
                        blocked.put(t.getOrder(), false);
                        blockedBy.put(t.getOrder(), new ArrayList<Integer>());
                    }
                    circuit(s.get(), minimalComponent, Optional.<Edge<T, ATTACHMENT>>absent());
                    s.addAndGet(1);
                } else {
                    throw new IllegalArgumentException("Unreachable code: Strongly connected components are always non-empty.");
                }
            } else {
                throw new IllegalArgumentException(String.format("Unreachable code: The set of edges with order >= %d must be non-empty.", s.get()));
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


    private boolean circuit(int vIndex, HashSet<ComponentFinder.Vertex<T, ATTACHMENT>> component, Optional<Edge<T, ATTACHMENT>> edge) {
        boolean circuitFound = false;
        if (edge.isPresent()) {
            edgeStack.push(edge.get());
        }

        block(vIndex);

        for (Edge<T, ATTACHMENT> edgeFromVToW : substituteList.get(vIndex).getOutgoingEdges()) {
            T w = edgeFromVToW.getTo();
            ComponentFinder.Vertex<T, ATTACHMENT> ver = new ComponentFinder.Vertex<>();
            ver.setDatum(w);
            if (!component.contains(ver)) {
                continue;
            }
            Integer indexOfW = ordering.get(w);
            if (indexOfW.equals(s.get())) {
                ImmutableList.Builder<Edge<T, ATTACHMENT>> edgeBuilder = new ImmutableList.Builder<>();
                ArrayDeque<Edge<T, ATTACHMENT>> copyOfEdgeStackToPop = edgeStack.clone();
                copyOfEdgeStackToPop.push(edgeFromVToW);
                while (!copyOfEdgeStackToPop.isEmpty()) {
                    edgeBuilder.add(copyOfEdgeStackToPop.pop());
                }
                circuits.add(edgeBuilder.build().reverse());
                circuitFound = true;
            } else if (!isBlocked(indexOfW)) {
                if (circuit(indexOfW, component, Optional.of(edgeFromVToW))) {
                    circuitFound = true;
                }
            }
        }

        if (circuitFound) {
            unblock(vIndex);
        } else {
            for (Edge<T, ATTACHMENT> w : substituteList.get(vIndex).getOutgoingEdges()) {
                ComponentFinder.Vertex<T, ATTACHMENT> tattachmentVertex = new ComponentFinder.Vertex<>();
                tattachmentVertex.setDatum(w.getTo());
                if (!component.contains(tattachmentVertex)) {
                    continue;
                }
                Integer indexOfW = ordering.get(w.getTo());
                if (!blockedBy.containsKey(indexOfW)) {
                    ArrayList<Integer> integers = new ArrayList<>();
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

    private void block(int vIndex) {
        blocked.put(vIndex, true);
    }

    private void unblock(int s) {
        blocked.put(s, false);
        if (blockedBy.containsKey(s)) {
            ArrayList<Integer> elements = blockedBy.get(s);
            List<Integer> nodesForS = ImmutableList.copyOf(elements);
            for (Integer node : nodesForS) {
                elements.remove(node);
                if (isBlocked(node)) {
                    unblock(node);
                }
            }
        }
    }

    private boolean isBlocked(int key) {
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

    private Optional<Integer> getMinimalVertexIndex(HashSet<ComponentFinder.Vertex<T, ATTACHMENT>> minimalComponent) {
        Optional<Integer> min = Optional.absent();
        for (ComponentFinder.Vertex<T, ATTACHMENT> t : minimalComponent) {
            if (!min.isPresent()) {
                min = Optional.of(t.getOrder());
            } else {
                min = Optional.of(Math.min(t.getOrder(), min.get()));
            }
        }
        return min;
    }
}
