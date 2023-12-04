package com.fincity.saas.commons.mq.events;

public class EventNames {

	public static final String CLIENT_CREATED = "CLIENT_CREATED";
	public static final String USER_CREATED = "USER_CREATED";
	public static final String CLIENT_REGISTERED = "CLIENT_REGISTERED";
	public static final String USER_REGISTERED = "USER_REGISTERED";
	public static final String USER_PASSWORD_CHANGED = "USER_PASSWORD_CHANGED";
	public static final String USER_PASSWORD_RESET_DONE = "USER_PASSWORD_RESET_DONE";
	public static final String USER_RESET_PASSWORD_REQUEST = "USER_RESET_PASSWORD_REQUEST";
	public static final String USER_CODE_GENERATION = "USER_CODE_GENERATION";

	private EventNames() {
	}
}
