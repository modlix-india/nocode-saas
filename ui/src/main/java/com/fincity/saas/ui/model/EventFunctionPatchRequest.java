package com.fincity.saas.ui.model;

import java.util.Map;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * Request body for PUT /api/ui/pages/{id}/events/{functionName}.
 * Per-event-function version checking enables concurrent edits to
 * different event functions on the same page.
 */
@Data
@Accessors(chain = true)
public class EventFunctionPatchRequest {

    private Map<String, Object> definition;
    private int expectedEventVersion;
    private String message;
}
