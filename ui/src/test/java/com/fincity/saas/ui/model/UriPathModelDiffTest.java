package com.fincity.saas.ui.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Round-trip tests for the two nested models under URIPath.
 *
 * A nested IDifferentiable is reached as {@code existing.extractDifference(incoming)},
 * so the RECEIVER is the base and the ARGUMENT is the derived - the opposite of a
 * document's {@code extractDifference(base)}. Both of these were written the document
 * way: every nested DifferenceExtractor.extract had base and derived swapped, and
 * KIRunFxDefinition's five scalars additionally stored the BASE's value as the delta.
 *
 * Asserting on the delta alone is not enough - the wrong value can still look
 * plausible - so each test extracts and then applies, which is what a derived client
 * actually experiences.
 */
class UriPathModelDiffTest {

    private static KIRunFxDefinition fx(String name, String ns, Map<String, String> headers) {
        KIRunFxDefinition d = new KIRunFxDefinition();
        d.setName(name);
        d.setNamespace(ns);
        d.setHeadersMapping(headers);
        return d;
    }

    @Test
    @DisplayName("A derived function name survives the round trip")
    void keepsDerivedScalar() {

        KIRunFxDefinition base = fx("baseFn", "ns", null);
        KIRunFxDefinition derived = fx("derivedFn", "ns", null);

        KIRunFxDefinition delta = base.extractDifference(derived).block();

        assertEquals("derivedFn", delta.getName(), "the delta must carry the DERIVED name");
        assertNull(delta.getNamespace(), "an unchanged namespace is not an override");

        KIRunFxDefinition rebuilt = delta.applyOverride(base).block();
        assertEquals("derivedFn", rebuilt.getName());
        assertEquals("ns", rebuilt.getNamespace());
    }

    @Test
    @DisplayName("A derived header mapping survives the round trip")
    void keepsDerivedMapEntry() {

        KIRunFxDefinition base = fx("fn", "ns", Map.of("a", "1"));
        KIRunFxDefinition derived = fx("fn", "ns", Map.of("a", "2"));

        KIRunFxDefinition delta = base.extractDifference(derived).block();
        KIRunFxDefinition rebuilt = delta.applyOverride(base).block();

        assertEquals("2", rebuilt.getHeadersMapping().get("a"), "the derived mapping must win");
    }

    @Test
    @DisplayName("A derived whitelist survives the round trip")
    void keepsDerivedList() {

        PathDefinition base = new PathDefinition();
        base.setWhitelist(List.of("10.0.0.1"));

        PathDefinition derived = new PathDefinition();
        derived.setWhitelist(List.of("10.0.0.2", "10.0.0.3"));

        PathDefinition delta = base.extractDifference(derived).block();
        PathDefinition rebuilt = delta.applyOverride(base).block();

        assertEquals(
                List.of("10.0.0.2", "10.0.0.3"),
                rebuilt.getWhitelist(),
                "a derived client's whitelist must not come back as the base's");
    }

    @Test
    @DisplayName("A nested KIRunFxDefinition inside a PathDefinition survives too")
    void keepsDerivedNestedModel() {

        PathDefinition base = new PathDefinition();
        base.setKiRunFxDefinition(fx("baseFn", "ns", null));

        PathDefinition derived = new PathDefinition();
        derived.setKiRunFxDefinition(fx("derivedFn", "ns", null));

        PathDefinition delta = base.extractDifference(derived).block();
        PathDefinition rebuilt = delta.applyOverride(base).block();

        assertEquals("derivedFn", rebuilt.getKiRunFxDefinition().getName());
    }

    /**
     * applyOverride is the other half, and it had the mirror-image fault: it read
     * {@code if (override.getX() != null) this.setX(override.getX())}, where
     * {@code this} is the DELTA and {@code override} the BASE - so the base
     * overwrote the derived value whenever the base had one, and a derived value
     * could never win. The correct form fills the delta's NULLS from the base.
     */
    @Test
    @DisplayName("A derived redirection target survives the round trip")
    void keepsDerivedRedirectionTarget() {

        RedirectionDefinition base = new RedirectionDefinition();
        base.setTargetUrl("https://base.example/");

        RedirectionDefinition derived = new RedirectionDefinition();
        derived.setTargetUrl("https://derived.example/");

        RedirectionDefinition delta = base.extractDifference(derived).block();
        assertEquals("https://derived.example/", delta.getTargetUrl());

        RedirectionDefinition rebuilt = delta.applyOverride(base).block();
        assertEquals(
                "https://derived.example/",
                rebuilt.getTargetUrl(),
                "the base must not overwrite a value the derived client set");
    }
}
