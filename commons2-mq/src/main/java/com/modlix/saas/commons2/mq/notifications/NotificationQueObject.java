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
    private String connectionName;
    private String xDebug;
    private Map<String, Object> payload;
}
