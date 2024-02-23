package com.fincity.saas.commons.mongo.util;

import java.util.Map;

import org.apache.commons.jxpath.ri.compiler.Step;
import org.junit.jupiter.api.Test;

import com.fincity.saas.ui.model.ComponentDefinition;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class DifferenceExtractorTest {

    @Test
    void testDifferenceExtractor() {

        ComponentDefinition base = new ComponentDefinition();

        base.setKey("7hwvfLdbWu30fEKpWYTI5");
        base.setName("Home");
        base.setType("Link");
        base.setProperties(
                Map.of("linkPath", Map.of("value", "/#wMvpDZzOZNVcEcc0d62yn"), "label", Map.of("value", "Home")));

        base.setOverride(true);
        base.setDisplayOrder(1);

        ComponentDefinition ovr = new ComponentDefinition();
        ovr.setKey("7hwvfLdbWu30fEKpWYTI5");
        ovr.setName("Home");
        ovr.setType("Link");
        ovr.setProperties(
                Map.of("linkPath", Map.of("value", "/#wMvpDZzOZNVcEcc0d62yn"), "label", Map.of("value", "Test")));

        ovr.setOverride(true);
        ovr.setDisplayOrder(4);

        Mono<ComponentDefinition> diff = DifferenceExtractor.extract(ovr, base).map(ComponentDefinition.class::cast);

        StepVerifier.create(diff.map(e -> e.getProperties()))
                .expectNext(Map.of("label", Map.of("value", "Test")))
                .verifyComplete();

        StepVerifier.create(diff.map(e -> e.getDisplayOrder()))
                .expectNext(4)
                .verifyComplete();
    }

}
