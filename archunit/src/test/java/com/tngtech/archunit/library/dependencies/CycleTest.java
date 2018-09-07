package com.tngtech.archunit.library.dependencies;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.tngtech.archunit.core.Convertible;
import org.junit.Test;

import static com.tngtech.archunit.library.dependencies.GraphTest.randomNode;
import static org.assertj.core.api.Assertions.assertThat;

public class CycleTest extends PathTest {
    @Test
    public void rejects_single_edge() {
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("don't form a cycle");

        new Cycle<>(Lists.<Edge<String, String>>newArrayList(new SimpleEdge(randomNode(), randomNode())));
    }

    @Test
    public void minimal_nontrivial_cycle() {
        String nodeA = "Node-A";
        String nodeB = "Node-B";
        Cycle<String, ?> cycle = new Cycle<>(Lists.<Edge<String, String>>newArrayList(new SimpleEdge(nodeA, nodeB), new SimpleEdge(nodeB, nodeA)));

        assertThat(cycle.getEdges()).hasSize(2);
    }

    @Test
    public void converts_attachments() {
        String first = randomNode();
        String second = randomNode();
        List<EdgeWithAttachment> edgesWithAttachments = Lists.newArrayList(
                new EdgeWithAttachment(first, second, new Attachment("one"), new Attachment("two")),
                new EdgeWithAttachment(second, first, new Attachment("three"), new Attachment("four")));

        Set<Attachment> convertedToIdentity = new Cycle<>(edgesWithAttachments).convertTo(Attachment.class);

        assertThat(convertedToIdentity).containsOnly(
                new Attachment("one"),
                new Attachment("two"),
                new Attachment("three"),
                new Attachment("four"));

        Set<ConversionTarget> converted = new Cycle<>(edgesWithAttachments).convertTo(ConversionTarget.class);

        assertThat(converted).containsOnly(
                new ConversionTarget("one"),
                new ConversionTarget("two"),
                new ConversionTarget("three"),
                new ConversionTarget("four"));
    }

    @Override
    protected void newPath(List<Edge<String, String>> edges) {
        new Cycle<>(edges);
    }

    private static class EdgeWithAttachment extends Edge<String, Attachment> {
        EdgeWithAttachment(String from, String to, Attachment... attachments) {
            super(from, to, ImmutableList.copyOf(attachments));
        }
    }

    private static class Attachment implements Convertible {
        private final String message;

        private Attachment(String message) {
            this.message = message;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> Set<T> convertTo(Class<T> type) {
            if (type == ConversionTarget.class) {
                return (Set<T>) Collections.singleton(new ConversionTarget(message));
            }
            return Collections.emptySet();
        }

        @Override
        public int hashCode() {
            return Objects.hash(message);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null || getClass() != obj.getClass()) {
                return false;
            }
            final Attachment other = (Attachment) obj;
            return Objects.equals(this.message, other.message);
        }
    }

    private static class ConversionTarget {
        private final String message;

        private ConversionTarget(String message) {
            this.message = message;
        }

        @Override
        public int hashCode() {
            return Objects.hash(message);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null || getClass() != obj.getClass()) {
                return false;
            }
            final ConversionTarget other = (ConversionTarget) obj;
            return Objects.equals(this.message, other.message);
        }
    }
}