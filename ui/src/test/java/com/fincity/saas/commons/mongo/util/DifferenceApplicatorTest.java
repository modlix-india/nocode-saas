package com.fincity.saas.commons.mongo.util;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fincity.saas.ui.model.ComponentDefinition;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class DifferenceApplicatorTest {

    @Test
    void testComponentDefinition() {

        ComponentDefinition base = new ComponentDefinition();

        base.setKey("7hwvfLdbWu30fEKpWYTI5");
        base.setName("Home");
        base.setType("Link");
        base.setProperties(
                Map.of("linkPath", Map.of("value", "/#wMvpDZzOZNVcEcc0d62yn"), "label", Map.of("value", "Home")));

        base.setOverride(true);
        base.setDisplayOrder(1);

        ComponentDefinition ovr = new ComponentDefinition();
        ovr.setProperties(Map.of("label", Map.of("value", "Test")));
        ovr.setDisplayOrder(4);

        Mono<ComponentDefinition> monofin = DifferenceApplicator.apply(ovr, base).map(ComponentDefinition.class::cast);

        Mono<Map<String, Object>> monoProps = monofin.map(ComponentDefinition::getProperties);

        StepVerifier.create(monoProps)
                .expectNext(Map.of("linkPath", Map.of("value", "/#wMvpDZzOZNVcEcc0d62yn"), "label",
                        Map.of("value", "Test")))
                .verifyComplete();

        StepVerifier.create(monofin.map(e -> e.getDisplayOrder()))
                .expectNext(4)
                .verifyComplete();
    }
}
