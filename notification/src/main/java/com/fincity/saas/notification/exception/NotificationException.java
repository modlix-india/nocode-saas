package com.fincity.saas.notification.exception;

import com.fincity.saas.commons.exeception.GenericException;
import org.springframework.http.HttpStatus;

public class NotificationException extends GenericException {

    public NotificationException(String message) {
        super(HttpStatus.INTERNAL_SERVER_ERROR, message);
    }

    public NotificationException(String message, Throwable ex) {
        super(HttpStatus.INTERNAL_SERVER_ERROR, message, ex);
    }
}
