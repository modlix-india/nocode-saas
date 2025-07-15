package com.fincity.saas.message.enums;

import com.fasterxml.jackson.annotation.JsonIgnore;

public interface IMessageSeries {

    @JsonIgnore
    default MessageSeries getMessageSeries() {
        return MessageSeries.XXX;
    }

    @JsonIgnore
    default String getMessageName() {
        return this.getMessageSeries().getDisplayName();
    }
}
