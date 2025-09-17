package com.modlix.saas.commons2.mq.events.exception;

public class EventCreationException extends RuntimeException {

    private static final long serialVersionUID = -5976954341316996314L;

    public EventCreationException(String errorField) {
        super(errorField + " cannot be null or blank");
    }
}
