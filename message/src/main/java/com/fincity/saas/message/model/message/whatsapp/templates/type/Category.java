package com.fincity.saas.message.model.message.whatsapp.templates.type;

import com.fasterxml.jackson.annotation.JsonValue;


public enum Category {

    
    AUTHENTICATION("AUTHENTICATION"),

    
    UTILITY("UTILITY"),

    
    @Deprecated()
    TRANSACTIONAL("TRANSACTIONAL"),

    
    MARKETING("MARKETING"),
    
    @Deprecated()
    OTP("OTP"),

    
    @Deprecated()
    ACCOUNT_UPDATE("ACCOUNT_UPDATE"),
    
    @Deprecated()
    PAYMENT_UPDATE("PAYMENT_UPDATE"),
    
    @Deprecated()
    PERSONAL_FINANCE_UPDATE("PERSONAL_FINANCE_UPDATE"),
    
    @Deprecated()
    SHIPPING_UPDATE("SHIPPING_UPDATE"),
    
    @Deprecated()
    RESERVATION_UPDATE("RESERVATION_UPDATE"),
    
    @Deprecated()
    ISSUE_RESOLUTION("ISSUE_RESOLUTION"),
    
    @Deprecated()
    APPOINTMENT_UPDATE("APPOINTMENT_UPDATE"),
    
    @Deprecated()
    TRANSPORTATION_UPDATE("TRANSPORTATION_UPDATE"),
    
    @Deprecated()
    TICKET_UPDATE("TICKET_UPDATE"),
    
    @Deprecated()
    ALERT_UPDATE("ALERT_UPDATE"),
    
    @Deprecated()
    AUTO_REPLY("AUTO_REPLY");

    private final String value;

    Category(String value) {
        this.value = value;
    }

    
    @JsonValue
    public String getValue() {
        return value;
    }
}
