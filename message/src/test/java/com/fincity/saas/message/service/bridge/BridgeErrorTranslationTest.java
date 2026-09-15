package com.fincity.saas.message.service.bridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fincity.saas.commons.exeception.GenericException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Surfacing a bridge failure as something a person can act on.
 *
 * <p>Production, 2026-09-10: WhatsApp refused a send from a restricted number with its own code
 * 463. The bridge answered 500 "internal", the message service wrapped it in a plain
 * RuntimeException, and the platform's ControllerAdvice replaced the message with "Please try again.
 * A server error-2026-09-10-298322e." So the one person who could act on it was told to retry,
 * against a restriction retrying cannot clear, while the real reason sat in a log on the host.
 *
 * <p>The advice passes a GenericException's status and message through untouched and rewrites
 * everything else, so the exception type is the fix.
 */
class BridgeErrorTranslationTest {

    private HttpStatus passThrough(int bridgeStatus) {
        return ReflectionTestUtils.invokeMethod(BridgeClient.class, "passThrough", bridgeStatus);
    }

    @Test
    @DisplayName("a status the platform understands is passed straight through")
    void knownStatusesArePreserved() {
        assertEquals(HttpStatus.CONFLICT, passThrough(409), "a restricted number must stay a 409");
        assertEquals(HttpStatus.NOT_FOUND, passThrough(404));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, passThrough(429));
        assertEquals(HttpStatus.BAD_GATEWAY, passThrough(502));
    }

    /**
     * WhatsApp uses codes of its own, and the bridge is not the only thing that can answer on that
     * socket. A value HttpStatus cannot represent must not take the whole response down.
     */
    @Test
    @DisplayName("a status the platform cannot represent becomes a bad gateway, not a crash")
    void unknownStatusesFallBack() {
        assertEquals(HttpStatus.BAD_GATEWAY, passThrough(463));
        assertEquals(HttpStatus.BAD_GATEWAY, passThrough(0));
        assertEquals(HttpStatus.BAD_GATEWAY, passThrough(999));
    }

    /**
     * The type is the whole fix. Anything that is not a GenericException has its message replaced
     * by the advice, which is how a real explanation became "Please try again".
     */
    @Test
    @DisplayName("a GenericException carries its own status and sentence")
    void genericExceptionSurvivesTheAdvice() {
        String sentence = "WhatsApp is restricting this number and would not start a new conversation.";
        GenericException e = new GenericException(HttpStatus.CONFLICT, sentence);

        assertEquals(HttpStatus.CONFLICT.value(), e.getStatusCode().value());
        assertTrue(e.getMessage().contains("restricting"), "the sentence must reach the caller");
        assertInstanceOf(RuntimeException.class, e);
    }
}
