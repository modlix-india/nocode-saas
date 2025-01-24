package com.fincity.saas.core.exception;

import org.springframework.http.HttpStatus;

import com.fincity.saas.commons.exeception.GenericException;

public class StorageException extends GenericException {

	public StorageException(String message) {
		this(HttpStatus.INTERNAL_SERVER_ERROR, message);
	}

	public StorageException(HttpStatus status, String message) {
		super(status, message);
	}
}
