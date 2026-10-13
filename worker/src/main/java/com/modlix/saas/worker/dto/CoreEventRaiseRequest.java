package com.modlix.saas.worker.dto;

import java.util.Map;
import lombok.Data;
import lombok.experimental.Accessors;

/** Body of core's POST /api/core/events/internal/raise. */
@Data
@Accessors(chain = true)
public class CoreEventRaiseRequest {

    private String eventName;
    private String appCode;
    private String clientCode;
    private Map<String, Object> data;
}
