package com.fincity.saas.message.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class CallRequest {

    private String appCode;

    private String clientCode;

    private String fromNumber;

    private String toNumber;

    private String callerId;

    private String connectionName;
}
