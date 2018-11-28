package com.tngtech.archunit.library.dependencies;

import com.google.common.collect.ImmutableSet;
import org.junit.Ignore;
import org.junit.Test;

import java.util.Collections;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.Iterables.getOnlyElement;
import static com.tngtech.archunit.library.dependencies.SimpleEdge.singleEdge;
import static com.tngtech.archunit.library.dependencies.SimpleEdge.singleEdgeList;
import static java.util.Collections.singleton;
import static org.assertj.core.api.Assertions.assertThat;

public class GraphTest {
    private static final Random random = new Random();

    @Test
    public void graph_without_cycles() {
        Graph<String, String> graph = new Graph<>();

        graph.add(randomNode(), Collections.<Edge<String, String>>emptySet());
        graph.add(randomNode(), Collections.<Edge<String, String>>emptySet());
        graph.add(randomNode(), Collections.<Edge<String, String>>emptySet());

        assertThat(graph.getCycles()).isEmpty();
    }

    @Test
    public void three_node_cycle_is_detected() {
        Graph<String, String> graph = new Graph<>();

        String nodeA = "Node-A";
        String nodeB = "Node-B";
        String nodeC = "Node-C";
        graph.add(nodeA, Collections.<Edge<String, String>>emptySet());
        graph.add(nodeB, Collections.<Edge<String, String>>singleton(new SimpleEdge(nodeA, nodeB)));
        graph.add(nodeC, ImmutableSet.<Edge<String, String>>of(new SimpleEdge(nodeB, nodeC), new SimpleEdge(nodeC, nodeA)));

        Cycle<String, String> cycle = getOnlyElement(graph.getCycles());

        assertThat(cycle.getEdges()).hasSize(3);
        assertEdgeExists(cycle, nodeA, nodeB);
        assertEdgeExists(cycle, nodeB, nodeC);
        assertEdgeExists(cycle, nodeC, nodeA);
    }

    @Test
    public void sub_cycle_of_three_node_cycle_is_detected() {
        Graph<String, String> graph = new Graph<>();

        String nodeA = "Node-A";
        String nodeB = "Node-B";
        String nodeC = "Node-C";
        graph.add(nodeA, Collections.<Edge<String, String>>emptySet());
        graph.add(nodeB, ImmutableSet.<Edge<String, String>>of(new SimpleEdge(nodeB, nodeA), new SimpleEdge(nodeA, nodeB)));
        graph.add(nodeC, Collections.<Edge<String, String>>emptySet());

        Cycle<String, String> cycle = getOnlyElement(graph.getCycles());
        assertThat(cycle.getEdges()).hasSize(2);
        assertEdgeExists(cycle, nodeA, nodeB);
        assertEdgeExists(cycle, nodeB, nodeA);
    }

    @Test
    public void nested_cycles_are_detected() {
        Graph<String, String> graph = new Graph<>();

        String nodeA = "Node-A";
        String nodeB = "Node-B";
        String nodeC = "Node-C";
        graph.add(nodeA, Collections.<Edge<String, String>>emptySet());
        graph.add(nodeB, ImmutableSet.<Edge<String, String>>of(new SimpleEdge(nodeB, nodeA), new SimpleEdge(nodeA, nodeB)));
        graph.add(nodeC, ImmutableSet.<Edge<String, String>>of(new SimpleEdge(nodeC, nodeA), new SimpleEdge(nodeB, nodeC)));

        assertThat(graph.getCycles()).hasSize(2);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void multiple_cycles_are_detected() {
        Graph<String, String> graph = new Graph<>();

        Cycle<String, String> threeElements = randomCycle(3);
        Cycle<String, String> fourElements = randomCycle(4);
        Cycle<String, String> fiveElements = randomCycle(5);

        addCycles(graph, threeElements, fourElements, fiveElements);
        addCrossLink(graph, threeElements, fourElements);
        addCrossLink(graph, fourElements, fiveElements);

        Set<Cycle<String, String>> cycles = graph.getCycles();

        assertThat(cycles).containsOnly(threeElements, fourElements, fiveElements);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void performance() {



        Graph<String, String> graph = new Graph<>();
        for (int j = 0; j < 500; j++) {
            Cycle<String, String> cycle = randomCycle(10);
            Cycle<String, String> cycle2 = randomCycle(10);
            addCycles(graph, cycle);
            addCycles(graph, cycle2);
            addCrossLink(graph, cycle, cycle2);
        }


        for (int i = 0; i < 1; i++) {

            Set<Cycle<String, String>> cycles = graph.getCycles();
        }

    }

    @Test
    @SuppressWarnings("unchecked")
    public void performanceRandom() {


        Graph<String, String> graph = randomGraph();

        for (int i = 0; i < 1; i++) {

            Set<Cycle<String, String>> cycles = graph.getCycles();
        }

    }

    private Graph<String, String> randomGraph() {
        Graph<String, String> graph = new Graph<>();
        graph.add(randomNode(), ImmutableSet.<Edge<String, String>>of());

        for (int i=0; i<20000;i++){
            String node = randomNode();
            graph.add(node, ImmutableSet.<Edge<String, String>>of(new SimpleEdge(node,randomElenent(graph.getNodes()))));

        }
        return graph;

    }

    private String randomElenent(Set<String> nodes) {


        int size = nodes.size();
        int item = new Random().nextInt(size); // In real life, the Random object should be rather more shared than this
        int i = 0;
        for(String obj : nodes)
        {
            if (i == item)
                return obj;
            i++;
        }

        return null;
    }

    @Test
    public void double_linked_three_node_cycle_results_in_five_cycles() {
        Graph<String, String> graph = new Graph<>();

        Cycle<String, String> threeElements = randomCycle(3);

        addCycles(graph, threeElements);
        for (Edge<String, String> edge : threeElements.getEdges()) {
            graph.add(edge.getTo(), singleEdge(edge.getTo(), edge.getFrom()));
        }

        assertThat(graph.getCycles()).hasSize(5);
    }

    private Cycle<String, String> randomCycle(int numberOfNodes) {
        checkArgument(numberOfNodes > 1, "A cycle can't be formed by less than 2 nodes");
        Path<String, String> path = new Path<>(singleEdgeList(randomNode(), randomNode()));
        for (int i = 0; i < numberOfNodes - 2; i++) {
            path.append(new SimpleEdge(path.getEnd(), randomNode()));
        }
        return new Cycle<>(path.append(new SimpleEdge(path.getEnd(), path.getStart())));
    }

    @SafeVarargs
    private final void addCycles(Graph<String, String> graph, Cycle<String, String>... cycles) {
        for (Cycle<String, String> cycle : cycles) {
            for (Edge<String, String> edge : cycle.getEdges()) {
                graph.add(edge.getFrom(), Collections.<Edge<String, String>>emptySet());
                graph.add(edge.getTo(), singleton(edge));
            }
        }
    }

    private void addCrossLink(Graph<String, String> graph, Cycle<String, String> first, Cycle<String, String> second) {
        Random rand = new Random();
        String origin = first.getEdges().get(rand.nextInt(first.getEdges().size())).getFrom();
        String target = second.getEdges().get(rand.nextInt(second.getEdges().size())).getFrom();
        graph.add(target, ImmutableSet.copyOf(singleEdgeList(origin, target)));
    }

    private static void assertEdgeExists(Cycle<?, ?> cycle, Object from, Object to) {
        for (Edge<?, ?> edge : cycle.getEdges()) {
            if (edge.getFrom().equals(from) && edge.getTo().equals(to)) {
                return;
            }
        }
        throw new AssertionError("Expected Cycle to contain an edge from " + from + " to " + to);
    }

    static String randomNode() {
        return "" + random.nextLong() + System.nanoTime();
    }
}