package com.fincity.saas.message.model.request.call.provider.telecmi;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serial;
import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Data;
import lombok.ToString;
import lombok.experimental.Accessors;

/**
 * TeleCMI's click-to-call: authenticates with the agent id and secret, no app id, and takes {@code to} and
 * {@code callerid} as numbers (digits with country code, no {@code +}).
 */
@Data
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TelecmiClickToCallRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 4417302985561042731L;

    @JsonProperty("user_id")
    private String userId;

    @ToString.Exclude
    private String secret;

    private Long to;

    @JsonProperty("callerid")
    private Long callerId;

    /** True rings the agent's softphone first; false, with {@link #followme}, their mobile. */
    private Boolean webrtc;

    private Boolean followme;

    @JsonProperty("extra_params")
    private Map<String, Object> extraParams;

    public static TelecmiClickToCallRequest ofBrowser(String agentId, String secret, Long to, Long callerId) {
        return of(agentId, secret, to, callerId).setWebrtc(Boolean.TRUE).setFollowme(Boolean.FALSE);
    }

    public static TelecmiClickToCallRequest ofMobile(String agentId, String secret, Long to, Long callerId) {
        return of(agentId, secret, to, callerId).setWebrtc(Boolean.FALSE).setFollowme(Boolean.TRUE);
    }

    private static TelecmiClickToCallRequest of(String agentId, String secret, Long to, Long callerId) {
        return new TelecmiClickToCallRequest()
                .setUserId(agentId)
                .setSecret(secret)
                .setTo(to)
                .setCallerId(callerId);
    }

    /** The request as kept on the call row, never with the secret. */
    public Map<String, Object> toRecorded() {
        Map<String, Object> recorded = new LinkedHashMap<>();
        recorded.put("user_id", this.userId);
        recorded.put("to", this.to);
        recorded.put("callerid", this.callerId);
        recorded.put("webrtc", this.webrtc);
        recorded.put("followme", this.followme);
        if (this.extraParams != null) recorded.put("extra_params", this.extraParams);
        return recorded;
    }
}
