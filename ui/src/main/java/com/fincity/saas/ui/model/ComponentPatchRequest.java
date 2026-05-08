package com.fincity.saas.ui.model;

import java.util.Map;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * Request body for PATCH /api/ui/pages/{id}/components/{componentKey}.
 * Per-component version checking enables concurrent edits to different
 * components on the same page.
 */
@Data
@Accessors(chain = true)
public class ComponentPatchRequest {

    private Map<String, Object> componentData;
    private int expectedComponentVersion;
    private String message;
}
