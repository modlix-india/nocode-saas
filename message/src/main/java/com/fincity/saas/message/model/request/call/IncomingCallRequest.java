package com.fincity.saas.message.model.request.call;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Map;
import lombok.Data;
import lombok.experimental.Accessors;
import org.jooq.types.ULong;

@Data
@Accessors(chain = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class IncomingCallRequest {

    private String appCode;
    private String clientCode;
    private ULong userId;
    private String connectionName;
    private Map<String, Object> providerIncomingRequest;
}
