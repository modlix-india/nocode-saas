package com.fincity.saas.message.model.request.call;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fincity.saas.message.model.common.PhoneNumber;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class IncomingCallRequest {

    private String appCode;
    private String clientCode;
    private List<PhoneNumber> destination;
    private String connectionName;
    private Map<String, Object> providerIncomingRequest;
}
