package com.fincity.security.exception;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.http.HttpStatus;

import lombok.Data;
import lombok.experimental.Accessors;

public class GenericException extends RuntimeException {

	private static final long serialVersionUID = 96467503087705861L;

	private final HttpStatus statusCode;

	private final String exceptionId;

	public GenericException(HttpStatus status, String message) {

		super(message);
		this.statusCode = status;
		this.exceptionId = uniqueId();
	}

	public GenericException(HttpStatus status, String message, Throwable ex) {

		super(message, ex);
		this.statusCode = status;
		this.exceptionId = uniqueId();
	}

	public GenericException(HttpStatus status, String exceptionId, String msg, Throwable e) {

		super(msg, e);
		this.statusCode = status;
		this.exceptionId = exceptionId;
	}

	public HttpStatus getStatusCode() {
		return statusCode;
	}

	public String getExceptionId() {
		return this.exceptionId;
	}

	public static final String uniqueId() {

		return (new SimpleDateFormat("yyyy-MM-dd")).format(Calendar.getInstance()
		        .getTime()) + "-" + Long.toHexString(System.currentTimeMillis() % (1000 * 60 * 60 * 24l));
	}

	public GenericExceptionData toExceptionData() {

		String st = Stream.of(this.getStackTrace())
		        .map(StackTraceElement::toString)
		        .collect(Collectors.joining("\n"));

		Throwable t = this.getCause();
		if (t != null) {
			st += "---------- Cause " + t.getMessage() + "\n" + Stream.of(t.getStackTrace())
			        .map(StackTraceElement::toString)
			        .collect(Collectors.joining("\n"));
		}

		return new GenericExceptionData().setExceptionId(this.exceptionId)
		        .setMessage(this.getMessage())
		        .setStackTrace(st)
		        .setDebugMessage((t == null ? this : t).getMessage());
	}

	@Data
	@Accessors(chain = true)
	public static class GenericExceptionData {

		private String exceptionId;
		private String message;
		private String stackTrace;
		private String debugMessage;
	}
}
