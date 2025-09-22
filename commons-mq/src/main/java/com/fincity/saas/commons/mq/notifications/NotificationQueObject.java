package com.fincity.saas.commons.mq.notifications;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigInteger;
import java.util.Map;

@Data
@Accessors(chain = true)
@NoArgsConstructor
public class NotificationQueObject implements Serializable {

    @Serial
    private static final long serialVersionUID = 5451810150227431980L;

    private String appCode;
    private String clientCode;
    private BigInteger targetId;
    private String targetType;
    private String targetCode;
    private String filterAuthorization;
    private String notificationName;
    private String xDebug;
    private Map<String, Object> payload;
    private Map<String, Connection> channelConnections;

    private static final class Connection implements Serializable {

    }
}
