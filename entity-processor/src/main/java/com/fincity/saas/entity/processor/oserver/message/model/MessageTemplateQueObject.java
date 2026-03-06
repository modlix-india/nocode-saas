package com.fincity.saas.entity.processor.oserver.message.model;

import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import java.io.Serial;
import java.io.Serializable;
import java.util.Map;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class MessageTemplateQueObject implements Serializable {

    @Serial
    private static final long serialVersionUID = 8256941067844694617L;

    private String eventName;
    private String clientCode;
    private String appCode;
    private String xDebug;

    private String ticketId;
    private String productId;
    private String stageId;
    private String statusId;

    private String channel;
    private Long messageTemplateId;

    private Map<String, Object> variables; // NOSONAR

    private ContextAuthentication authentication;
}
