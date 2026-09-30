package com.fincity.saas.message.model.response.call.provider.telecmi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fincity.saas.message.configuration.call.telecmi.TelecmiApiConfig;
import java.io.Serial;
import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * What TeleCMI's app-authenticated endpoints answer. The result lives in the body ({@code code: 400} can
 * arrive inside an HTTP 200), so read {@link #isSuccess()}, never the HTTP status.
 */
@Data
@Accessors(chain = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class TelecmiResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1648305172947760533L;

    private Integer code;
    private String status;
    private String msg;

    private TelecmiAgent agent;

    private List<TelecmiAgent> agents;

    /** {@code /v2/balance}; {@code expire} is epoch millis. */
    private Double balance;

    private Long sms;
    private Long expire;

    /** Click-to-call's id, carried by every webhook of the call. */
    @JsonProperty("request_id")
    private String requestId;

    public boolean isSuccess() {
        return this.code != null
                && this.code == TelecmiApiConfig.CODE_SUCCESS
                && (this.status == null || "success".equalsIgnoreCase(this.status));
    }

    public boolean isNotFound() {
        return this.code != null && this.code == TelecmiApiConfig.CODE_NOT_FOUND;
    }

    /** Whether TeleCMI's message starts with the given refusal, ignoring its trailing " !". */
    public boolean refused(String message) {
        return this.msg != null && this.msg.trim().startsWith(message);
    }

    public Map<String, Object> toRecorded() {
        Map<String, Object> recorded = new LinkedHashMap<>();
        recorded.put("code", this.code);
        if (this.status != null) recorded.put("status", this.status);
        recorded.put("msg", this.msg);
        if (this.requestId != null) recorded.put("request_id", this.requestId);
        return recorded;
    }

    /** What to put in front of a caller when TeleCMI refuses. Never the raw body. */
    public String errorDetail() {
        if (this.msg != null && !this.msg.isBlank()) return this.msg.trim();
        return "TeleCMI returned " + this.status + " (" + this.code + ")";
    }
}
