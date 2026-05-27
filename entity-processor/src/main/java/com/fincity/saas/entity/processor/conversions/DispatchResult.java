package com.fincity.saas.entity.processor.conversions;

import java.util.Map;

/**
 * Outcome of one dispatcher attempt. {@code success=true} → outbox row is marked
 * SENT; otherwise FAILED with the error message and next-attempt backoff. The
 * raw platform response (if any) is stored verbatim on the outbox row for later
 * diagnostics.
 */
public record DispatchResult(boolean success, String message, Map<String, Object> platformResponse) {

    public static DispatchResult ok(String message, Map<String, Object> response) {
        return new DispatchResult(true, message, response);
    }

    public static DispatchResult fail(String message, Map<String, Object> response) {
        return new DispatchResult(false, message, response);
    }
}
