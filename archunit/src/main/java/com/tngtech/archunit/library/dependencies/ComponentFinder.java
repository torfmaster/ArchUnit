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
import com.google.common.collect.Multimap;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

class ComponentFinder<T, ATTACHMENT> {
    private Set<T> nodes;
    private Multimap<T, Edge<T, ATTACHMENT>> outgoingEdges;
    private ImmutableBiMap<T, Integer> ordering;
    private final HashSet<HashSet<T>> components = new HashSet<>();
    private final ArrayDeque<T> stack = new ArrayDeque<>();
    private final HashMap<T, Integer> indices = new HashMap<>();
    private final HashMap<T, Integer> lowLinks = new HashMap<>();
    private final HashMap<T, Boolean> onStack = new HashMap<>();
    private final AtomicInteger index = new AtomicInteger(-1);

    ComponentFinder(Set<T> nodes, Multimap<T, Edge<T, ATTACHMENT>> outgoingEdges, ImmutableBiMap<T, Integer> ordering) {
        this.nodes = nodes;
        this.outgoingEdges = outgoingEdges;
        this.ordering = ordering;
    }

    Optional<HashSet<T>> findLeastScc(int i) {
        HashSet<HashSet<T>> sccs = getSccs(i);
        Optional<HashSet<T>> chosen = Optional.absent();
        Optional<Integer> globalMin = Optional.absent();
        for (HashSet<T> scc : sccs) {
            Optional<Integer> min = Optional.absent();
            for (T t : scc) {
                if (!min.isPresent()) {
                    min = Optional.of(ordering.get(t));
                } else {
                    min = Optional.of(Math.min(min.get(), ordering.get(t)));
                }
            }
            if (!globalMin.isPresent()) {
                globalMin = min;
                chosen = Optional.of(scc);
            } else {
                if (min.isPresent()) {
                    globalMin = Optional.of(Math.min(globalMin.get(), min.get()));
                    if (globalMin.get().equals(min.get())) {
                        chosen = Optional.of(scc);
                    }
                }
            }
        }
        return chosen;
    }


    HashSet<HashSet<T>> getSccs(int i) {
        for (T node : nodes) {
            if (!indices.containsKey(node) && ordering.get(node) >= i) {
                scc(node, i);
            }
        }
        return components;
    }

    private void scc(T v, int i) {
        int currentIndex = index.incrementAndGet();
        indices.put(v, currentIndex);
        lowLinks.put(v, currentIndex);
        onStack.put(v, true);
        stack.push(v);

        Collection<Edge<T, ATTACHMENT>> edges = outgoingEdges.get(v);
        for (Edge<T, ATTACHMENT> edge : edges) {
            T w = edge.getTo();
            if (ordering.get(w) < i) {
                continue;
            }
            if (!indices.containsKey(w)) {
                scc(w, i);
                int min = Math.min(lowLinks.get(v), lowLinks.get(w));
                lowLinks.put(v, min);
            } else if (onStack.containsKey(w) && onStack.get(w)) {
                int min = Math.min(indices.get(w), lowLinks.get(v));
                lowLinks.put(v, min);
            }
        }

        if (lowLinks.get(v).equals(indices.get(v))) {
            T w;
            HashSet<T> component = new HashSet<>();
            do {
                w = stack.pop();
                onStack.put(w, false);
                component.add(w);
            } while (!v.equals(w));
            components.add(component);
        }
    }

}
