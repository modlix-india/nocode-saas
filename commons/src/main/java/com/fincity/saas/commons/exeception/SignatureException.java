package com.fincity.saas.commons.exeception;

import org.springframework.http.HttpStatus;

public class SignatureException extends GenericException {

    public SignatureException(String message) {
        super(HttpStatus.INTERNAL_SERVER_ERROR, message);
    }

    public SignatureException(String message, Throwable cause) {
        this(HttpStatus.INTERNAL_SERVER_ERROR, message, cause);
    }

    public SignatureException(HttpStatus status, String message, Throwable cause) {
        super(status, message, cause);
    }
}
