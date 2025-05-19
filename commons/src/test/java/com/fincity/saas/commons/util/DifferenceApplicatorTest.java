package com.fincity.saas.commons.util;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fincity.saas.commons.util.DifferenceApplicator;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class DifferenceApplicatorTest {

    @Test
    void testApply() {
        Map<String, String> base = Map.of("a1", "b1", "a2", "b2", "a3", "b3");
        Map<String, String> ovr = new HashMap<>(Map.of("a1", "c1"));
        ovr.put("a2", null);

        Mono<Map<String, ?>> monofin = DifferenceApplicator.apply(ovr, base);

        Map<String, ?> expected = new HashMap<>(Map.of("a1", "c1", "a3", "b3"));

        StepVerifier.create(monofin)
                .expectNext(expected)
                .verifyComplete();
    }
}
