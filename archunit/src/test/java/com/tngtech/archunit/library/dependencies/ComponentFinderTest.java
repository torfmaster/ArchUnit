package com.tngtech.archunit.library.dependencies;

import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableSet;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;

import static org.assertj.core.api.Assertions.assertThat;

public class ComponentFinderTest {

    @Test
    public void isolated_components_are_found() {
        Graph<String, String> graph = new Graph<>();

        String nodeA = "Node-A";
        String nodeB = "Node-B";
        String nodeC = "Node-C";
        graph.add(nodeA, Collections.<Edge<String, String>>emptySet());
        graph.add(nodeB, ImmutableSet.<Edge<String, String>>of(new SimpleEdge(nodeA, nodeB)));
        graph.add(nodeC, ImmutableSet.<Edge<String, String>>of(new SimpleEdge(nodeA, nodeC), new SimpleEdge(nodeB, nodeC)));
        ImmutableBiMap.Builder<String, Integer> builder = new ImmutableBiMap.Builder<>();
        builder.put(nodeA, 0);
        builder.put(nodeB, 1);
        builder.put(nodeC, 2);

        HashSet<ArrayList<String>> sccs = new ComponentFinder<>(graph.getNodes(), graph.getOutgoingEdges(), builder.build()).getStronglyConnectedComponentsInInducedSubgraphBiggerThanI(0);
        assertThat(sccs).hasSize(3);
    }

    @Test
    public void subCycles_are_ignored() {
        Graph<String, String> graph = new Graph<>();

        String nodeA = "Node-A";
        String nodeB = "Node-B";
        String nodeC = "Node-C";
        graph.add(nodeA, Collections.<Edge<String, String>>emptySet());
        graph.add(nodeB, ImmutableSet.<Edge<String, String>>of(new SimpleEdge(nodeB, nodeA), new SimpleEdge(nodeA, nodeB)));
        graph.add(nodeC, ImmutableSet.<Edge<String, String>>of(new SimpleEdge(nodeC, nodeA), new SimpleEdge(nodeB, nodeC)));
        ImmutableBiMap.Builder<String, Integer> builder = new ImmutableBiMap.Builder<>();
        builder.put(nodeA, 0);
        builder.put(nodeB, 1);
        builder.put(nodeC, 2);

        HashSet<ArrayList<String>> sccs = new ComponentFinder<>(graph.getNodes(), graph.getOutgoingEdges(), builder.build()).getStronglyConnectedComponentsInInducedSubgraphBiggerThanI(0);
        assertThat(sccs).hasSize(1);
    }
}