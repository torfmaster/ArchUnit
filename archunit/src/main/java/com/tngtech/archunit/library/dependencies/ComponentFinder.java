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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

class ComponentFinder<T> {
    private ImmutableBiMap<T, Integer> ordering;
    private final ArrayList<ArrayList<T>> components = new ArrayList<>();
    private final ArrayDeque<Vertex<T>> stack = new ArrayDeque<>();
    private final AtomicInteger index = new AtomicInteger(-1);
    private ArrayList<Vertex<T>> vertexList;

    ComponentFinder(ImmutableBiMap<T, Integer> ordering, ArrayList<Vertex<T>> vertexList) {
        this.ordering = ordering;
        this.vertexList = vertexList;
    }

    void reset() {
        index.set(-1);
        components.clear();
        stack.clear();
    }

    Optional<ArrayList<T>> findLeastScc(int lowerIndexBound) {
        ArrayList<ArrayList<T>> stronglyConnectedComponents = getStronglyConnectedComponentsInInducedSubgraphBiggerThanI(lowerIndexBound);
        Optional<ArrayList<T>> chosen = Optional.absent();
        Optional<Integer> globalMin = Optional.absent();
        for (ArrayList<T> stronglyConncectedComponent : stronglyConnectedComponents) {
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


    private ArrayList<ArrayList<T>> getStronglyConnectedComponentsInInducedSubgraphBiggerThanI(int lowerIndexBound) {
        int size = vertexList.size();
        for (int j = lowerIndexBound; j < size; j++) {
            if (vertexList.get(j).getIndex() == null) {
                if (scc(vertexList.get(j), lowerIndexBound)) {
                    break;
                }
            }
        }
        return components;
    }

    private boolean scc(Vertex<T> v, int lowerIndexBound) {
        int currentIndex = index.incrementAndGet();
        v.setIndex(currentIndex);
        v.setLowLink(currentIndex);
        v.setOnStack(true);
        stack.push(v);

        for (Integer edge : v.outgoingEdgesArray) {
            Vertex<T> w = vertexList.get(edge);
            if (w.getOrder() < lowerIndexBound) {
                continue;
            }
            if ((w.getIndex() == null)) {
                if (scc(w, lowerIndexBound)) {
                    return true;
                }
                int min = Math.min(v.getLowLink(), w.getLowLink());
                v.setLowLink(min);
            } else if (isOnStack(w)) {
                int min = Math.min(w.getIndex(), v.getLowLink());
                v.setLowLink(min);
            }
        }

        if (v.lowLink.equals(v.getIndex())) {
            Vertex<T> w;
            boolean minimumAchieved = false;
            ArrayList<T> component = new ArrayList<>();
            do {
                w = stack.pop();
                if (w.getOrder().equals(lowerIndexBound)) {
                    minimumAchieved=true;
                }
                w.setOnStack(false);
                w.setIndex(null);
                component.add(w.getDatum());
            } while (!v.equals(w));
            components.add(component);
            return minimumAchieved;
        }
        return false;
    }

    private boolean isOnStack(Vertex<T> w) {
        return !(w.onStack == null) && w.onStack;
    }

    static class Vertex<T> {
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
        ArrayList<Integer> outgoingEdgesArray;

    }

}
