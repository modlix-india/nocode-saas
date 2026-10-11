package com.fincity.saas.ui.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;

import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.ui.model.KIRunFxDefinition;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import reactor.test.StepVerifier;

/**
 * KIRun URIPath routes: declared mappings on body methods, and the optional error event.
 */
class URIPathKIRunRouteTest {

    private static final Gson GSON = new Gson();

    private static JsonObject body(String json) {
        return GSON.fromJson(json, JsonObject.class);
    }

    @Test
    @DisplayName("A body method with no mapping sends the body unchanged")
    void noMappingKeepsBody() {

        MockServerHttpRequest req = MockServerHttpRequest.post("/api/open/x/abc?q=1").header("X-Forwarded-Host", "h")
                .build();

        KIRunFxDefinition def = new KIRunFxDefinition();
        JsonObject args = URIPathService.withMappedParams(body("{\"a\":1}"), req, "/api/open/x/{code}", def);

        assertEquals(body("{\"a\":1}"), args);
    }

    @Test
    @DisplayName("Declared path, query and header mappings are merged into a POST body, mapped values win")
    void mappedParamsMerged() {

        MockServerHttpRequest req = MockServerHttpRequest.post("/api/open/tickets/req/website/P123?src=fb")
                .header("X-Forwarded-Host", "site.example.com").build();

        KIRunFxDefinition def = new KIRunFxDefinition();
        def.setPathParamMapping(Map.of("code", "code"));
        def.setQueryParamMapping(Map.of("src", "source", "missing", "never"));
        def.setHeadersMapping(Map.of("X-Forwarded-Host", "requestHost"));

        JsonObject in = body("{\"code\":\"fromBody\",\"leadDetails\":{\"fullName\":\"A\"}}");
        JsonObject args = URIPathService.withMappedParams(in, req, "/api/open/tickets/req/website/{code}", def);

        assertEquals("P123", args.get("code").getAsString());
        assertEquals("fb", args.get("source").getAsString());
        assertEquals("site.example.com", args.get("requestHost").getAsString());
        assertFalse(args.has("never"), "an absent query parameter adds nothing");
        assertEquals("A", args.getAsJsonObject("leadDetails").get("fullName").getAsString());
        assertEquals("fromBody", in.get("code").getAsString(), "the request body itself is not modified");
    }

    @Test
    @DisplayName("Without errorEventName an error event still answers nothing")
    void noErrorEventName() {

        JsonArray resp = GSON.fromJson("[{\"name\":\"error\",\"result\":{\"status\":404}}]", JsonArray.class);
        StepVerifier.create(URIPathService.errorEventResponse(resp, new KIRunFxDefinition())).verifyComplete();
    }

    @Test
    @DisplayName("An error event with a body answers that body verbatim with the event's status")
    void errorEventWithBody() {

        KIRunFxDefinition def = new KIRunFxDefinition();
        def.setErrorEventName("error");

        JsonArray resp = GSON.fromJson(
                "[{\"name\":\"error\",\"result\":{\"status\":403.0,\"body\":\"Verification token mismatch\"}}]",
                JsonArray.class);

        ResponseEntity<String> r = URIPathService.errorEventResponse(resp, def).block();
        assertEquals(HttpStatus.FORBIDDEN, r.getStatusCode());
        assertEquals("Verification token mismatch", r.getBody());

        JsonArray objBody = GSON.fromJson("[{\"name\":\"error\",\"result\":{\"status\":409,\"body\":{\"d\":true}}}]",
                JsonArray.class);
        assertEquals("{\"d\":true}", URIPathService.errorEventResponse(objBody, def).block().getBody());
    }

    @Test
    @DisplayName("An error event without a body becomes a GenericException with its status and message")
    void errorEventWithoutBody() {

        KIRunFxDefinition def = new KIRunFxDefinition();
        def.setErrorEventName("error");

        JsonArray resp = GSON.fromJson(
                "[{\"name\":\"error\",\"result\":{\"status\":404,\"message\":\"Unknown with identity x not found.\"}}]",
                JsonArray.class);

        StepVerifier.create(URIPathService.errorEventResponse(resp, def))
                .expectErrorMatches(e -> e instanceof GenericException ge && ge.getStatusCode() == HttpStatus.NOT_FOUND
                        && "Unknown with identity x not found.".equals(ge.getMessage()))
                .verify();

        JsonArray bad = GSON.fromJson("[{\"name\":\"error\",\"result\":{\"status\":200}}]", JsonArray.class);
        StepVerifier.create(URIPathService.errorEventResponse(bad, def))
                .expectErrorMatches(e -> e instanceof GenericException ge && ge.getStatusCode() == HttpStatus.BAD_REQUEST)
                .verify();
    }

    @Test
    @DisplayName("errorEventName survives the override round trip")
    void errorEventNameRoundTrip() {

        KIRunFxDefinition base = new KIRunFxDefinition();
        base.setName("fn");
        KIRunFxDefinition derived = new KIRunFxDefinition();
        derived.setName("fn");
        derived.setErrorEventName("error");

        KIRunFxDefinition delta = base.extractDifference(derived).block();
        assertEquals("error", delta.getErrorEventName());
        assertNull(base.extractDifference(base).block().getErrorEventName());

        KIRunFxDefinition copy = new KIRunFxDefinition(derived);
        assertTrue("error".equals(copy.getErrorEventName()));
    }
}
