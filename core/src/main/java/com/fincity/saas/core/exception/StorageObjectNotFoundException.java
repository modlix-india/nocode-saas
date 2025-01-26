package com.fincity.saas.core.exception;

import org.springframework.http.HttpStatus;

import com.fincity.saas.commons.exeception.GenericException;

public class StorageObjectNotFoundException extends GenericException {

	public StorageObjectNotFoundException(String message) {
		this(HttpStatus.INTERNAL_SERVER_ERROR, message);
	}

	public StorageObjectNotFoundException(HttpStatus status, String message) {
		super(status, message);
	}
}
