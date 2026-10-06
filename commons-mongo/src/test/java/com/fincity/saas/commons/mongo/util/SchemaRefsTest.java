package com.fincity.saas.commons.mongo.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Which schemas a definition points at, and which documents care when one changes.
 *
 * Both halves exist to answer one question: after a schema is saved, whose table has
 * to be rebuilt. Getting it wrong in the quiet direction leaves a storage sitting on
 * a table that no longer matches its definition, and nothing says so until a write
 * hits a column of the wrong type.
 */
class SchemaRefsTest {

    @Nested
    @DisplayName("finding references")
    class Collecting {

        @Test
        @DisplayName("a top level ref")
        void topLevel() {
            assertEquals(Set.of("App.Customer"), SchemaRefs.collect(Map.of("ref", "App.Customer")));
        }

        @Test
        @DisplayName("a ref inside a property")
        void inProperty() {
            Map<String, Object> def = Map.of("type", "OBJECT", "properties", Map.of("c", Map.of("ref", "App.City")));

            assertEquals(Set.of("App.City"), SchemaRefs.collect(def));
        }

        @Test
        @DisplayName("a ref inside an array's items")
        void inItems() {
            Map<String, Object> def =
                    Map.of("type", "ARRAY", "items", Map.of("ref", "App.Line"));

            assertEquals(Set.of("App.Line"), SchemaRefs.collect(def));
        }

        @Test
        @DisplayName("a ref inside a list, which is how tuple items are written")
        void inList() {
            Map<String, Object> def = Map.of("items", List.of(Map.of("ref", "App.A"), Map.of("ref", "App.B")));

            assertEquals(Set.of("App.A", "App.B"), SchemaRefs.collect(def));
        }

        @Test
        @DisplayName("a path ref is cut at the slash")
        void pathRef() {
            assertTrue(SchemaRefs.collect(Map.of("ref", "App.Customer/properties/name"))
                    .contains("App.Customer"));
        }

        @Test
        @DisplayName("a dotted ref yields every prefix that could be a document name")
        void dottedRef() {
            // Refs written as Model.UserSegment._id exist in the wild, and only the
            // prefix that names a real schema will match anything.
            Set<String> refs = SchemaRefs.collect(Map.of("ref", "Model.UserSegment._id"));

            assertTrue(refs.contains("Model.UserSegment._id"));
            assertTrue(refs.contains("Model.UserSegment"));

            // The bare namespace is not a candidate: a document is stored under
            // namespace + "." + name, so its name always has a dot in it.
            assertFalse(refs.contains("Model"));
        }

        @Test
        @DisplayName("an internal ref is not an edge to another document")
        void internalRef() {
            assertTrue(SchemaRefs.collect(Map.of("ref", "#/$defs/Inner")).isEmpty());
        }

        @Test
        @DisplayName("a definition with no refs yields nothing, and null is not an error")
        void none() {
            assertTrue(SchemaRefs.collect(Map.of("type", "STRING")).isEmpty());
            assertTrue(SchemaRefs.collect(null).isEmpty());
        }
    }

    @Nested
    @DisplayName("who cares when one changes")
    class Closure {

        @Test
        @DisplayName("the changed document is always in its own closure")
        void itself() {
            assertEquals(Set.of("App.Money"), SchemaRefs.referencingClosure(Map.of(), "App.Money"));
        }

        @Test
        @DisplayName("a document that references it directly")
        void direct() {
            Map<String, Set<String>> graph = Map.of("App.Order", Set.of("App.Money"));

            assertEquals(Set.of("App.Money", "App.Order"), SchemaRefs.referencingClosure(graph, "App.Money"));
        }

        @Test
        @DisplayName("and one that only reaches it through another")
        void transitive() {
            Map<String, Set<String>> graph =
                    Map.of("App.Order", Set.of("App.Money"), "App.Invoice", Set.of("App.Order"));

            // This is the case that makes the whole walk necessary. Following direct
            // references alone would miss App.Invoice, whose table is just as wrong.
            assertEquals(
                    Set.of("App.Money", "App.Order", "App.Invoice"),
                    SchemaRefs.referencingClosure(graph, "App.Money"));
        }

        @Test
        @DisplayName("documents on an unrelated branch are left out")
        void unrelated() {
            Map<String, Set<String>> graph =
                    Map.of("App.Order", Set.of("App.Money"), "App.Page", Set.of("App.Widget"));

            assertFalse(SchemaRefs.referencingClosure(graph, "App.Money").contains("App.Page"));
        }

        @Test
        @DisplayName("a cycle terminates")
        void cycle() {
            // A tree node whose children are tree nodes is a legitimate schema, and a
            // walk that does not expect one hangs the save that triggered it.
            Map<String, Set<String>> graph = Map.of("App.Node", Set.of("App.Node"));

            assertEquals(Set.of("App.Node"), SchemaRefs.referencingClosure(graph, "App.Node"));
        }

        @Test
        @DisplayName("a longer cycle terminates too")
        void mutualCycle() {
            Map<String, Set<String>> graph = Map.of("App.A", Set.of("App.B"), "App.B", Set.of("App.A"));

            assertEquals(Set.of("App.A", "App.B"), SchemaRefs.referencingClosure(graph, "App.A"));
        }
    }
}
