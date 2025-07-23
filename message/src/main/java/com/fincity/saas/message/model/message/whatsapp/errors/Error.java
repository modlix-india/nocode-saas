package com.fincity.saas.message.model.message.whatsapp.errors;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serial;
import java.io.Serializable;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class Error implements Serializable {

    @Serial
    private static final long serialVersionUID = 6956973960386534666L;

    @JsonProperty("code")
    private Integer code;

    @JsonProperty("details")
    private String details;

    @JsonProperty("fbtrace_id")
    private String fbtraceId;

    @JsonProperty("message")
    @JsonAlias({
        "message",
    })
    private String message;

    @JsonProperty("messaging_product")
    private String messagingProduct;

    @JsonProperty("error_data")
    private ErrorData errorData;

    @JsonProperty("type")
    private String type;

    @JsonProperty("is_transient")
    private Boolean isTransient;

    @JsonProperty("error_user_title")
    private String errorUserSubtitle;

    @JsonProperty("error_user_msg")
    private String errorUserMsg;
}
