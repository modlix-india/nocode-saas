package com.fincity.saas.message.model.response.call.provider.telecmi;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * Our answer to TeleCMI's inbound HTTP flow, in the shape TeleCMI executed live. {@code followme} is a presence
 * fallback, not an escalation: the mobile rings only when the agent has no registered softphone.
 */
@Data
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TelecmiFlowReply implements Serializable {

    @Serial
    private static final long serialVersionUID = 3308914476250981347L;

    public static final int DEFAULT_TIMEOUT_SECONDS = 30;

    private Integer code = 200;
    private Integer loop = 1;
    private Boolean followme;
    private Boolean hangup = Boolean.FALSE;
    private Integer timeout = DEFAULT_TIMEOUT_SECONDS;
    private List<Target> result;

    @Data
    @Accessors(chain = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Target implements Serializable {

        @Serial
        private static final long serialVersionUID = 6620149317583016410L;

        @JsonProperty("agent_id")
        private String agentId;

        /** Digits with the country code, no {@code +}. */
        private String phone;
    }
}
