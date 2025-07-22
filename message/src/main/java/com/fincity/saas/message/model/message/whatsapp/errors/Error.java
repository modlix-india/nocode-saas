package com.fincity.saas.message.model.message.whatsapp.errors;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;


import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Error(
        @JsonProperty("code") Integer code,
        @JsonProperty("details") String details,
        @Deprecated(forRemoval = true) @JsonProperty("error_subcode") Integer errorSubcode,
        @JsonProperty("fbtrace_id") String fbtraceId,
        @JsonProperty("message")
                @JsonAlias({
                    "message",
                })
                String message,
        @JsonProperty("messaging_product") String messagingProduct,
        @JsonProperty("error_data") ErrorData errorData,
        @JsonProperty("type") String type,
        @JsonProperty("is_transient") Boolean isTransient,
        @JsonProperty("error_user_title") String errorUserSubtitle,
        @JsonProperty("error_user_msg") String errorUserMsg) {}
