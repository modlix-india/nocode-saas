package com.fincity.saas.commons.mongo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.fincity.saas.commons.mongo.document.AbstractSchema;
import com.fincity.saas.commons.mongo.repository.IOverridableDataRepository;

/**
 * Covers the ref walk that orders schemas inside a transport. The refs used
 * here are real ones pulled out of the core schema collection.
 */
class AbstractSchemaServiceDependencyTest {

    static class TestSchema extends AbstractSchema<TestSchema> {

        private static final long serialVersionUID = 1L;

        TestSchema(Map<String, Object> definition) {
            this.setDefinition(definition);
        }
    }

    static class TestSchemaService extends AbstractSchemaService<TestSchema, IOverridableDataRepository<TestSchema>> {

        TestSchemaService() {
            super(TestSchema.class, null, null);
        }
    }

    private static final TestSchemaService SERVICE = new TestSchemaService();

    private static Collection<String> depsOf(Map<String, Object> definition) {
        return SERVICE.getTransportDependencies(new TestSchema(definition));
    }

    @Test
    void aSchemaWithNoRefsDependsOnNothing() {

        assertTrue(depsOf(Map.of("namespace", "Model", "name", "Address", "type", "OBJECT"))
                .isEmpty());
        assertTrue(SERVICE.getTransportDependencies(new TestSchema(null)).isEmpty());
        assertTrue(SERVICE.getTransportDependencies(null).isEmpty());
    }

    @Test
    void aTopLevelRefIsTheDocumentName() {

        assertEquals(Set.of("Model.FilterCondition"), Set.copyOf(depsOf(Map.of("ref", "Model.FilterCondition"))));
    }

    @Test
    void refsInsidePropertiesAreFound() {

        // Model.ProfessionalDetails, as it is actually stored
        Map<String, Object> definition = Map.of(
                "namespace", "Model",
                "name", "ProfessionalDetails",
                "type", "OBJECT",
                "properties", Map.of(
                        "address", Map.of("ref", "Model.Address"),
                        "status", Map.of("ref", "EnumModel.EmploymentStatus")));

        assertEquals(Set.of("Model.Address", "EnumModel.EmploymentStatus"), Set.copyOf(depsOf(definition)));
    }

    @Test
    void refsAreFoundThroughEveryNestingShapeIncludingTheUnionTypes() {

        Map<String, Object> definition = Map.ofEntries(
                Map.entry("namespace", "Model"),
                Map.entry("name", "Everything"),
                Map.entry("anyOf", List.of(Map.of("ref", "Model.A"))),
                Map.entry("allOf", List.of(Map.of("ref", "Model.B"))),
                Map.entry("oneOf", List.of(Map.of("ref", "Model.C"))),
                Map.entry("not", Map.of("ref", "Model.D")),
                Map.entry("contains", Map.of("ref", "Model.E")),
                Map.entry("propertyNames", Map.of("ref", "Model.F")),
                Map.entry("patternProperties", Map.of("^x", Map.of("ref", "Model.G"))),
                Map.entry("$defs", Map.of("inner", Map.of("ref", "Model.H"))),
                // items: bare, wrapped single, and wrapped tuple all occur
                Map.entry("items", Map.of("singleSchema", Map.of("ref", "Model.I"))),
                Map.entry("additionalItems", Map.of("schemaValue", Map.of("ref", "Model.J"))),
                // additionalProperties as a bare schema, and as a bare boolean
                Map.entry("additionalProperties", Map.of("ref", "Model.K")),
                Map.entry("properties", Map.of(
                        "deep", Map.of("items", List.of(Map.of("ref", "Model.L"))),
                        "flag", Map.of("additionalProperties", true))));

        assertEquals(
                Set.of("Model.A", "Model.B", "Model.C", "Model.D", "Model.E", "Model.F", "Model.G", "Model.H",
                        "Model.I", "Model.J", "Model.K", "Model.L"),
                Set.copyOf(depsOf(definition)));
    }

    @Test
    void aRefWithAPathDependsOnTheDocumentTheHeadNames() {

        assertEquals(Set.of("Model.User"), Set.copyOf(depsOf(Map.of("ref", "Model.User/properties/name"))));
    }

    @Test
    void aDottedNamespaceYieldsEveryCandidateDocumentName() {

        // rim.Projects.Project is namespace "rim.Projects", name "Project".
        // Model.UserSegment._id, which also exists in the data, is only
        // resolvable as Model.UserSegment.
        assertEquals(Set.of("rim.Projects.Project", "rim.Projects"),
                Set.copyOf(depsOf(Map.of("ref", "rim.Projects.Project"))));

        assertTrue(depsOf(Map.of("ref", "Model.UserSegment._id")).contains("Model.UserSegment"));
    }

    @Test
    void internalAndUnusableRefsAreNotEdges() {

        assertTrue(depsOf(Map.of("ref", "#/$defs/inner")).isEmpty());
        assertTrue(depsOf(Map.of("ref", "#/properties/name")).isEmpty());
        assertTrue(depsOf(Map.of("ref", "")).isEmpty());
        // No dot means it can never be a namespace + "." + name document
        assertTrue(depsOf(Map.of("ref", "Something")).isEmpty());
    }
}
