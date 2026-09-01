package com.modlix.saas.commons2.mq.notifications;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigInteger;
import java.util.Map;
import java.util.UUID;

import com.modlix.saas.commons2.util.UniqueUtil;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@NoArgsConstructor
public class NotificationQueObject implements Serializable {

    @Serial
    private static final long serialVersionUID = 5451810150227431980L;

    private final String id = UUID.randomUUID().toString().replace("-", "");
    private String appCode;
    private String clientCode;
    private String urlClientCode;
    private BigInteger triggeredUserId;
    private BigInteger targetId;
    private String targetType;
    private String targetCode;
    private String filterAuthorization;
    private String notificationName;
    private String notificationCategory;
    private String connectionName;
    private String xDebug;

    /**
     * Whether the request that raised this notification was on the draft surface.
     *
     * Carried on the message because the consumer runs on its own thread with no
     * inbound request, exactly like xDebug.
     *
     * It governs which NOTIFICATION and CONNECTION definition the sender resolves,
     * not who receives the message. Recipients still come from the real user
     * directory: a notification exercised from a sandbox is expected to reach real
     * people, and only the definition it renders from follows the surface.
     */
    private boolean draft;

    private Map<String, Object> payload;
}
