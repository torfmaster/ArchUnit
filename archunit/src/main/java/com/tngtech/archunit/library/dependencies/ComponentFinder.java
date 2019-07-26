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

import com.google.common.base.Optional;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

class ComponentFinder<T, ATTACHMENT> {
    private final ArrayList<HashSet<Vertex<T, ATTACHMENT>>> components = new ArrayList<>();
    private final ArrayDeque<Vertex<T, ATTACHMENT>> stack = new ArrayDeque<>();
    private final AtomicInteger index = new AtomicInteger(0);
    private ArrayList<Vertex<T, ATTACHMENT>> vertexList;

    ComponentFinder(ArrayList<Vertex<T, ATTACHMENT>> vertexList) {
        this.vertexList = vertexList;
    }

    private void reset() {
        index.set(0);
        components.clear();
        stack.clear();
        for (Vertex<T, ATTACHMENT> vertex : vertexList) {
            vertex.setIndex(null);
        }
    }

    Optional<HashSet<Vertex<T, ATTACHMENT>>> findLeastScc(int lowerIndexBound) {
        ArrayList<HashSet<Vertex<T, ATTACHMENT>>> stronglyConnectedComponents = getStronglyConnectedComponentsInInducedSubgraphBiggerThanI(lowerIndexBound);
        Optional<HashSet<Vertex<T, ATTACHMENT>>> chosen = Optional.absent();
        Optional<Integer> globalMin = Optional.absent();
        for (HashSet<Vertex<T, ATTACHMENT>> stronglyConncectedComponent : stronglyConnectedComponents) {
            if (stronglyConncectedComponent.size() == 1) {
                continue;
            }
            Optional<Integer> min = Optional.absent();
            for (Vertex<T, ATTACHMENT> t : stronglyConncectedComponent) {
                if (!min.isPresent()) {
                    min = Optional.of(t.getOrder());
                } else {
                    min = Optional.of(Math.min(min.get(), t.getOrder()));
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
        reset();
        return chosen;
    }


    private ArrayList<HashSet<Vertex<T, ATTACHMENT>>> getStronglyConnectedComponentsInInducedSubgraphBiggerThanI(int lowerIndexBound) {
        int size = vertexList.size();
        for (int j = lowerIndexBound; j < size; j++) {
            if (vertexList.get(j).getIndex() == null) {
                scc(vertexList.get(j), lowerIndexBound);
            }
        }
        return components;
    }

    private void scc(Vertex<T, ATTACHMENT> v, int lowerIndexBound) {
        int currentIndex = index.getAndIncrement();
        v.setIndex(currentIndex);
        v.setLowLink(currentIndex);
        v.setOnStack(true);
        stack.push(v);

        for (Integer edge : v.outgoingEdgesArray) {
            Vertex<T, ATTACHMENT> w = vertexList.get(edge);
            if (w.getOrder() < lowerIndexBound) {
                continue;
            }
            if ((w.getIndex() == null)) {
                scc(w, lowerIndexBound);
                int min = Math.min(v.getLowLink(), w.getLowLink());
                v.setLowLink(min);
            } else if (isOnStack(w)) {
                int min = Math.min(w.getIndex(), v.getLowLink());
                v.setLowLink(min);
            }
        }

        if (v.lowLink.equals(v.getIndex())) {
            Vertex<T, ATTACHMENT> w;
            HashSet<Vertex<T, ATTACHMENT>> component = new HashSet<>();
            do {
                w = stack.pop();
                w.setOnStack(false);
                component.add(w);
            } while (!v.equals(w));
            components.add(component);
        }
    }

    private boolean isOnStack(Vertex<T, ATTACHMENT> w) {
        return !(w.onStack == null) && w.onStack;
    }

    static class Vertex<T, ATTACHMENT> {
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Vertex<?, ?> vertex = (Vertex<?, ?>) o;
            return Objects.equals(datum, vertex.datum);
        }

        @Override
        public int hashCode() {

            return Objects.hash(datum);
        }

        Vertex(T datum, Integer order) {
            this.datum = datum;
            this.order = order;
        }

        Vertex() {
        }

        void setDatum(T datum) {
            this.datum = datum;
        }

        T datum;

        Integer order;

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

        ArrayList<Integer> outgoingEdgesArray;

        ArrayList<Edge<T, ATTACHMENT>> getOutgoingEdges() {
            return outgoingEdges;
        }

        ArrayList<Edge<T, ATTACHMENT>> outgoingEdges;

    }

}
