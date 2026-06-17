package com.fincity.security.model.billing;

import org.jooq.types.ULong;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * The clean AI token-usage shape reported by nocode-ai per turn. The server
 * prices the raw token breakdown into credits via the per-model pricing map, so
 * the client never computes money. Settlement is idempotent on
 * (sessionId, turnNumber).
 */
@Data
@Accessors(chain = true)
public class UsageReport {

    private String clientCode;
    private String appCode;
    private ULong userId;
    private String actionKey;
    private String provider;
    private String model;
    private long inputTokens;
    private long outputTokens;
    private long cacheReadTokens;
    private long cacheCreationTokens;
    private String requestId;
    private String sessionId;
    private Integer turnNumber;
    private String agentType;
    private long timestamp;
}
