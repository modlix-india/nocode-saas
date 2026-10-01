package com.fincity.saas.message.model.request.call.provider.telecmi;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serial;
import java.io.Serializable;
import lombok.Data;
import lombok.ToString;
import lombok.experimental.Accessors;

/**
 * A request to TeleCMI's app-authenticated API (v3 users, v2 balance). Unset fields are left out, which
 * matters on update: every field but {@code agent_id} is optional and an omitted one is left unchanged.
 */
@Data
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TelecmiUserRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 7340281956473850217L;

    @JsonProperty("appid")
    private Long appId;

    @ToString.Exclude
    private String secret;

    @JsonProperty("agent_id")
    private String agentId;

    private Integer extension;

    @JsonProperty("first_name")
    private String firstName;

    @JsonProperty("last_name")
    private String lastName;

    @JsonProperty("email_id")
    private String emailId;

    /** Digits with the country code, no {@code +}. */
    @JsonProperty("phone_number")
    private String phoneNumber;

    @ToString.Exclude
    private String password;

    /** False when omitted, which leaves the agent unreachable on their mobile when offline. */
    private Boolean followme;

    public static TelecmiUserRequest ofApp(Long appId, String secret) {
        return new TelecmiUserRequest().setAppId(appId).setSecret(secret);
    }

    public static TelecmiUserRequest ofAgent(Long appId, String secret, String agentId) {
        return ofApp(appId, secret).setAgentId(agentId);
    }
}
