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

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Multimap;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

class ComponentFinder<T, ATTACHMENT> {
    private Set<Vertex<T>> nodes2 = new HashSet<>();
    private Multimap<T, Edge<T, ATTACHMENT>> outgoingEdges;
    private ImmutableBiMap<T, Integer> ordering;
    private final HashSet<HashSet<T>> components = new HashSet<>();
    private final ArrayDeque<Vertex<T>> stack = new ArrayDeque<>();
    private final AtomicInteger index = new AtomicInteger(-1);
    private ImmutableMap<T, Vertex<T>> vertices;

    ComponentFinder(Set<T> nodes, Multimap<T, Edge<T, ATTACHMENT>> outgoingEdges, ImmutableBiMap<T, Integer> ordering) {
        this.outgoingEdges = outgoingEdges;
        this.ordering = ordering;
        ImmutableBiMap.Builder<T, Vertex<T>> builder = new ImmutableBiMap.Builder<>();
        for (T node : nodes) {
            Vertex<T> vertex = new Vertex<>(node, ordering.get(node));
            nodes2.add(vertex);
            builder.put(node, vertex);
        }
        vertices=builder.build();
    }

    Optional<HashSet<T>> findLeastScc(int i) {
        HashSet<HashSet<T>> stronglyConnectedComponents = getStronglyConnectedComponentsInInducedSubgraphBiggerThanI(i);
        Optional<HashSet<T>> chosen = Optional.absent();
        Optional<Integer> globalMin = Optional.absent();
        for (HashSet<T> stronglyConncectedComponent : stronglyConnectedComponents) {
            Optional<Integer> min = Optional.absent();
            for (T t : stronglyConncectedComponent) {
                if (!min.isPresent()) {
                    min = Optional.of(ordering.get(t));
                } else {
                    min = Optional.of(Math.min(min.get(), ordering.get(t)));
                }
            }
            if (!globalMin.isPresent()) {
                globalMin = min;
                chosen = Optional.of(stronglyConncectedComponent);
            } else {
                if (min.isPresent()) {
                    globalMin = Optional.of(Math.min(globalMin.get(), min.get()));
                    if (globalMin.get().equals(min.get())) {
                        chosen = Optional.of(stronglyConncectedComponent);
                    }
                }
            }
        }
        return chosen;
    }


    HashSet<HashSet<T>> getStronglyConnectedComponentsInInducedSubgraphBiggerThanI(int i) {
        for (Vertex<T> node : nodes2) {
            if ((node.getIndex()==null) && node.getOrder()>=i) {
                scc(node, i);
            }
        }
        return components;
    }

    private void scc(Vertex<T> v, int i) {
        int currentIndex = index.incrementAndGet();
        v.setIndex(currentIndex);
        v.setLowLink(currentIndex);
        v.setOnStack(true);
        stack.push(v);

        Collection<Edge<T, ATTACHMENT>> edges = outgoingEdges.get(v.getDatum());
        for (Edge<T, ATTACHMENT> edge : edges) {
            Vertex<T> w = vertices.get(edge.getTo());
            if (ordering.get(w.getDatum()) < i) {
                continue;
            }
            if ((w.getIndex()==null)) {
                scc(w, i);
                int min = Math.min(v.getLowLink(), w.getLowLink());
                v.setLowLink(min);
            } else if ( !(w.onStack==null) && w.onStack) {
                int min = Math.min(w.getIndex(), v.getLowLink());
                v.setLowLink(min);
            }
        }

        if (v.lowLink.equals(v.getIndex())) {
            Vertex<T> w;
            HashSet<T> component = new HashSet<>();
            do {
                w = stack.pop();
                w.setOnStack(false);
                component.add(w.getDatum());
            } while (!v.equals(w));
            components.add(component);
        }
    }

    static class Vertex<T>{
        Vertex(T datum, Integer order) {
            this.datum = datum;
            this.order = order;
        }

        T datum;
        Integer order;

        T getDatum() {
            return datum;
        }

        Integer getOrder() {
            return order;
        }

        Integer getIndex() {
            return index;
        }

        void setIndex(Integer index) {
            this.index = index;
        }

        Integer getLowLink() {
            return lowLink;
        }

        void setLowLink(Integer lowLink) {
            this.lowLink = lowLink;
        }

        void setOnStack(Boolean onStack) {
            this.onStack = onStack;
        }

        Integer index;
        Integer lowLink;
        Boolean onStack;
    }

}
