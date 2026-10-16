package com.fincity.saas.ui.model;

import java.util.Map;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * Request body for POST /api/ui/pages/{id}/components: one new component under an
 * existing parent.
 */
@Data
@Accessors(chain = true)
public class ComponentAddRequest {

    private String componentKey;
    private Map<String, Object> componentData;
    private String parentKey;
    private String message;
}
