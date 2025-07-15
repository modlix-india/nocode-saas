package com.fincity.saas.message.model.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * Request object for making a call.
 */
@Data
@Accessors(chain = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class CallRequest {

    /**
     * The application code.
     */
    private String appCode;

    /**
     * The client code.
     */
    private String clientCode;

    /**
     * The phone number that will be called first.
     */
    private String fromNumber;

    /**
     * The phone number that will be called after the first number answers.
     */
    private String toNumber;

    /**
     * The caller ID to display.
     */
    private String callerId;

    /**
     * The name of the connection to use.
     */
    private String connectionName;
}
