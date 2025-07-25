package com.fincity.saas.message.model.message.whatsapp.webhook;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fincity.saas.message.model.message.whatsapp.webhook.type.EventType;
import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class Value implements Serializable {

    @Serial
    private static final long serialVersionUID = 7051721185793663659L;

    @JsonProperty("metadata")
    private Metadata metadata;

    @JsonProperty("messaging_product")
    private String messagingProduct;

    @JsonProperty("messages")
    private List<Message> messages;

    @JsonProperty("contacts")
    private List<Contact> contacts;

    @JsonProperty("statuses")
    private List<Status> statuses;

    @JsonProperty("event")
    private EventType event;

    @JsonProperty("phone_number")
    private String phoneNumber;

    @JsonProperty("message_template_id")
    private String messageTemplateId;

    @JsonProperty("message_template_name")
    private String messageTemplateName;

    @JsonProperty("message_template_language")
    private String messageTemplateLanguage;

    @JsonProperty("reason")
    private String reason;

    @JsonProperty("display_phone_number")
    private String displayPhoneNumber;

    @JsonProperty("decision")
    private String decision;

    @JsonProperty("requested_verified_name")
    private String requestedVerifiedName;

    @JsonProperty("rejection_reason")
    private Object rejectionReason;

    @JsonProperty("disable_info")
    private DisableInfo disableInfo;

    @JsonProperty("current_limit")
    private String currentLimit;

    @JsonProperty("ban_info")
    private BanInfo banInfo;

    @JsonProperty("restriction_info")
    private List<RestrictionInfo> restrictionInfo;
}
