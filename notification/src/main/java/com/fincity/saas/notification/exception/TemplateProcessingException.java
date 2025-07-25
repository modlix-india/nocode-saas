package com.fincity.saas.notification.exception;

import com.fincity.saas.commons.exeception.GenericException;
import org.springframework.http.HttpStatus;

public class TemplateProcessingException extends GenericException {

    public TemplateProcessingException(String message) {
        super(HttpStatus.INTERNAL_SERVER_ERROR, message);
    }

    public TemplateProcessingException(String message, Throwable ex) {
        super(HttpStatus.INTERNAL_SERVER_ERROR, message, ex);
    }

    public TemplateProcessingException(Throwable ex) {
        super(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage(), ex);
    }
}
