package com.fincity.saas.message.model.request.call;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fincity.saas.message.model.common.PhoneNumber;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class CallRequest {

    private String appCode;
    private String clientCode;
    private PhoneNumber fromNumber;
    private PhoneNumber toNumber;
    private String callerId;
    private String connectionName;
}
