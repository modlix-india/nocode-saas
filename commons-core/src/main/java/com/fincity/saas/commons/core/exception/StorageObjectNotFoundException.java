package com.fincity.saas.commons.core.exception;

import com.fincity.saas.commons.exeception.GenericException;
import org.springframework.http.HttpStatus;

public class StorageObjectNotFoundException extends GenericException {

    public StorageObjectNotFoundException(String message) {
        this(HttpStatus.INTERNAL_SERVER_ERROR, message);
    }

    public StorageObjectNotFoundException(HttpStatus status, String message) {
        super(status, message);
    }
}
