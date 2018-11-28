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
    private LinkedList<Vertex<T, ATTACHMENT>> nodes2;
    private ImmutableBiMap<T, Integer> ordering;
    private final HashSet<LinkedList<T>> components = new HashSet<LinkedList<T>>();
    private final ArrayDeque<Vertex<T, ATTACHMENT>> stack = new ArrayDeque<>();
    private final AtomicInteger index = new AtomicInteger(-1);
    private ImmutableMap<T, Vertex<T, ATTACHMENT>> vertices;

    ComponentFinder(Set<T> nodes, Multimap<T, Edge<T, ATTACHMENT>> outgoingEdges, ImmutableBiMap<T, Integer> ordering) {
        this.ordering = ordering;
        LinkedList<Vertex<T, ATTACHMENT>> nodes2 = new LinkedList<>();
        ImmutableMap.Builder<T, Vertex<T, ATTACHMENT>> builder = new ImmutableMap.Builder<>();
        for (T node : nodes) {

            Vertex<T, ATTACHMENT> vertex = new Vertex<>(node, ordering.get(node), new LinkedList<>(outgoingEdges.get(node)));
            nodes2.add(vertex);
            builder.put(node, vertex);
        }
        this.nodes2=nodes2;
        vertices = builder.build();
    }

    ComponentFinder(ImmutableMap<T, Vertex<T, ATTACHMENT>> vertices, ImmutableBiMap<T, Integer> ordering, LinkedList<Vertex<T, ATTACHMENT>> nodes2) {
        this.vertices = vertices;
        this.ordering = ordering;
        this.nodes2 = nodes2;
        for (Vertex<T, ATTACHMENT> tattachmentVertex : nodes2) {
            tattachmentVertex.setOnStack(false);
            tattachmentVertex.lowLink=null;
            tattachmentVertex.index=null;
        }
    }

    Optional<LinkedList<T>> findLeastScc(int i) {
        HashSet<LinkedList<T>> stronglyConnectedComponents = getStronglyConnectedComponentsInInducedSubgraphBiggerThanI(i);
        Optional<LinkedList<T>> chosen = Optional.absent();
        Optional<Integer> globalMin = Optional.absent();
        for (LinkedList<T> stronglyConncectedComponent : stronglyConnectedComponents) {
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


    HashSet<LinkedList<T>> getStronglyConnectedComponentsInInducedSubgraphBiggerThanI(int i) {
        for (Vertex<T, ATTACHMENT> node : nodes2) {
            if ((node.getIndex() == null) && node.getOrder() >= i) {
                scc(node, i);
            }
        }
        return components;
    }

    private boolean scc(Vertex<T, ATTACHMENT> v, int i) {
        int currentIndex = index.incrementAndGet();
        v.setIndex(currentIndex);
        v.setLowLink(currentIndex);
        v.setOnStack(true);
        stack.push(v);

        Collection<Edge<T, ATTACHMENT>> edges = v.getOutgoingEdges();
        for (Edge<T, ATTACHMENT> edge : edges) {
            Vertex<T, ATTACHMENT> w = vertices.get(edge.getTo());
            if (w.getOrder() < i) {
                continue;
            }
            if ((w.getIndex() == null)) {
                if (scc(w, i)){
                    return true;
                };
                int min = Math.min(v.getLowLink(), w.getLowLink());
                v.setLowLink(min);
            } else if (!(w.onStack == null) && w.onStack) {
                int min = Math.min(w.getIndex(), v.getLowLink());
                v.setLowLink(min);
            }
        }

        if (v.lowLink.equals(v.getIndex())) {
            Vertex<T, ATTACHMENT> w;
            boolean minimumAchieved = false;
            LinkedList<T> component = new LinkedList<>();
            do {
                w = stack.pop();
                if (w.getOrder().equals(i)) {
                    minimumAchieved=true;
                }
                w.setOnStack(false);
                component.add(w.getDatum());
            } while (!v.equals(w));
            components.add(component);
            if (minimumAchieved){
                return true;
            }
        }
        return false;
    }

    public static class Vertex<T, ATTACHMENT> {
        Vertex(T datum, Integer order, Collection<Edge<T, ATTACHMENT>> outgoingEdges) {
            this.datum = datum;
            this.order = order;
            this.outgoingEdges = outgoingEdges;
        }

        T datum;

        public Boolean getOnStack() {
            return onStack;
        }

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

        public Collection<Edge<T, ATTACHMENT>> getOutgoingEdges() {
            return outgoingEdges;
        }

        Collection<Edge<T, ATTACHMENT>> outgoingEdges;
    }

}
