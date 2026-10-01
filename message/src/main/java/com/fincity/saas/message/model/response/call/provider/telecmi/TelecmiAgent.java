package com.fincity.saas.message.model.response.call.provider.telecmi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serial;
import java.io.Serializable;
import lombok.Data;
import lombok.ToString;
import lombok.experimental.Accessors;

/**
 * One TeleCMI user. Listings return the password in plain text: never copy it anywhere but the agent's
 * own endpoint row.
 */
@Data
@Accessors(chain = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class TelecmiAgent implements Serializable {

    @Serial
    private static final long serialVersionUID = 5196602735480216318L;

    /** {@code <extension>_<appid>}: the identity the softphone registers as. */
    @JsonProperty("agent_id")
    private String agentId;

    @JsonProperty("user_id")
    private String userId;

    @JsonProperty("first_name")
    private String firstName;

    @JsonProperty("last_name")
    private String lastName;

    private Integer extension;

    /** Seen both with and without the country code; compare with {@code PhoneUtil.isSameNumber}. */
    private String phone;

    @ToString.Exclude
    private String password;

    private Boolean followme;
}
