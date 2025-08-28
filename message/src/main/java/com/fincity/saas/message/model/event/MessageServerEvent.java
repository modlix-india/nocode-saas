package com.fincity.saas.message.model.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fincity.saas.commons.util.IClassConvertor;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class MessageServerEvent implements IClassConvertor {

    private String id;

    private String eventType;

    private LocalDateTime timestamp = LocalDateTime.now();

    private String appCode;

    private String clientCode;

    private BigInteger userId;

    private Map<String, Object> data;

    public static MessageServerEvent of(String eventType) {
        return new MessageServerEvent().setEventType(eventType);
    }

    public static MessageServerEvent of(String eventType, Map<String, Object> data) {
        return new MessageServerEvent().setEventType(eventType).setData(data);
    }
}
