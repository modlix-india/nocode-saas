package com.fincity.saas.commons.core.enums;

import lombok.Getter;

@Getter
public enum EventActionTaskType {
    SEND_EMAIL("Send Email"),

    CALL_CORE_FUNCTION("Call Core Function");

    private final String displayName;

    EventActionTaskType(String displayName) {
        this.displayName = displayName;
    }
}
