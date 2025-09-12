package com.modlix.saas.commons2.mq.events;

import java.io.Serializable;
import java.util.Map;

import com.modlix.saas.commons2.security.jwt.ContextAuthentication;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class EventQueObject implements Serializable {

    private static final long serialVersionUID = -2382306278225358489L;

    private String eventName;
    private String clientCode;
    private String appCode;
    private String xDebug;
    private Map<String, Object> data; // NOSONAR
    private ContextAuthentication authentication;
}
