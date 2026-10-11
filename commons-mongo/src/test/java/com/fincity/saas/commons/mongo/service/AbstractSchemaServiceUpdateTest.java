package com.fincity.saas.commons.mongo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.mongo.document.AbstractSchema;
import com.fincity.saas.commons.mongo.repository.IOverridableDataRepository;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * A PUT on a derived (client override) schema reaches updatableEntity as its DIFFERENCE from the
 * base, which by construction has no name or namespace. That used to be refused as "Namespace or
 * name missing" on every derived schema. A base schema must still carry both, and neither may
 * change.
 */
class AbstractSchemaServiceUpdateTest {

    static class TestSchema extends AbstractSchema<TestSchema> {

        private static final long serialVersionUID = 1L;

        TestSchema(Map<String, Object> definition) {
            this.setDefinition(definition);
        }
    }

    static class TestSchemaService extends AbstractSchemaService<TestSchema, IOverridableDataRepository<TestSchema>> {

        private final TestSchema stored;

        TestSchemaService(TestSchema stored) {
            super(TestSchema.class, null, null);
            this.stored = stored;
            this.messageResourceService = new AbstractMongoMessageResourceService() {
            };
        }

        @Override
        public Mono<TestSchema> read(String id) {
            return Mono.just(this.stored);
        }
    }

    private static TestSchema stored(String baseClientCode) {
        TestSchema s = new TestSchema(new HashMap<>(Map.of("namespace", "LeadZumpV2", "name", "Deal", "type", "OBJECT")));
        s.setId("s1");
        s.setName("LeadZumpV2.Deal");
        s.setAppCode("leadzumptwo");
        s.setClientCode(baseClientCode == null ? "SYSTEM" : "FIN2");
        s.setBaseClientCode(baseClientCode);
        s.setVersion(3);
        return s;
    }

    private static TestSchema update(Map<String, Object> definition) {
        TestSchema e = new TestSchema(definition);
        e.setId("s1");
        e.setVersion(3);
        return e;
    }

    private static void refusedWith(Mono<TestSchema> mono, String messagePart) {
        StepVerifier.create(mono)
                .expectErrorSatisfies(e -> {
                    assertEquals(HttpStatus.BAD_REQUEST, ((GenericException) e).getStatusCode());
                    if (messagePart != null)
                        assertEquals(true, e.getMessage().toLowerCase().contains(messagePart), e.getMessage());
                })
                .verify();
    }

    @Test
    void aDerivedSchemasDifferenceWithoutNameOrNamespaceIsAccepted() {

        Map<String, Object> diff = Map.of("properties", Map.of("fin2Custom", Map.of("type", "STRING")));

        StepVerifier.create(new TestSchemaService(stored("SYSTEM")).updatableEntity(update(diff)))
                .assertNext(saved -> {
                    assertEquals(diff, saved.getDefinition());
                    assertEquals(4, saved.getVersion());
                    assertEquals("LeadZumpV2.Deal", saved.getName());
                })
                .verifyComplete();
    }

    @Test
    void aDerivedSchemaMayRepeatItsOwnName() {

        StepVerifier.create(new TestSchemaService(stored("SYSTEM"))
                .updatableEntity(update(Map.of("namespace", "LeadZumpV2", "name", "Deal"))))
                .assertNext(saved -> assertEquals(4, saved.getVersion()))
                .verifyComplete();

        StepVerifier.create(new TestSchemaService(stored("SYSTEM"))
                .updatableEntity(update(Map.of("name", "Deal"))))
                .assertNext(saved -> assertEquals(4, saved.getVersion()))
                .verifyComplete();
    }

    @Test
    void aDerivedSchemaStillCannotBeRenamed() {

        refusedWith(new TestSchemaService(stored("SYSTEM")).updatableEntity(update(Map.of("name", "Lead"))), null);
        refusedWith(new TestSchemaService(stored("SYSTEM")).updatableEntity(update(Map.of("namespace", "Other"))),
                null);
    }

    @Test
    void aBaseSchemaMustStillCarryNameAndNamespace() {

        refusedWith(new TestSchemaService(stored(null)).updatableEntity(update(Map.of("type", "OBJECT"))), null);
        refusedWith(new TestSchemaService(stored(null)).updatableEntity(update(Map.of("name", "Deal"))), null);

        StepVerifier.create(new TestSchemaService(stored(null))
                .updatableEntity(update(Map.of("namespace", "LeadZumpV2", "name", "Deal", "type", "OBJECT"))))
                .assertNext(saved -> assertEquals(4, saved.getVersion()))
                .verifyComplete();

        refusedWith(new TestSchemaService(stored(null))
                .updatableEntity(update(Map.of("namespace", "LeadZumpV2", "name", "Lead"))), null);
    }

    @Test
    void theNameHelperResolvesEachCase() {

        TestSchemaService service = new TestSchemaService(stored("SYSTEM"));

        assertEquals("LeadZumpV2.Deal", service.updatedSchemaName(update(Map.of()), stored("SYSTEM")));
        assertEquals("LeadZumpV2.Deal", service.updatedSchemaName(update(null), stored("SYSTEM")));
        assertEquals("Other.Deal", service.updatedSchemaName(update(Map.of("namespace", "Other")), stored("SYSTEM")));
        assertNull(service.updatedSchemaName(update(Map.of()), stored(null)));
    }
}
