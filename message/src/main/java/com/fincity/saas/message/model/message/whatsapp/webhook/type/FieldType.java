package com.fincity.saas.message.model.message.whatsapp.webhook.type;

import com.fasterxml.jackson.annotation.JsonValue;

public enum FieldType {
    MESSAGE_TEMPLATE_STATUS_UPDATE("message_template_status_update"),
    PHONE_NUMBER_NAME_UPDATE("phone_number_name_update"),
    PHONE_NUMBER_QUALITY_UPDATE("phone_number_quality_update"),
    ACCOUNT_UPDATE("account_update"),
    ACCOUNT_REVIEW_UPDATE("account_review_update"),
    MESSAGES("messages"),
    security("security");

    private final String value;

    FieldType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}
