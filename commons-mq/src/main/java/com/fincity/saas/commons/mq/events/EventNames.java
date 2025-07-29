package com.fincity.saas.commons.mq.events;

import com.fincity.nocode.kirun.engine.util.string.StringFormatter;

public class EventNames {

	public static final String CLIENT_CREATED = "CLIENT_CREATED";
	public static final String USER_CREATED = "USER_CREATED";
	public static final String CLIENT_REGISTERED = "CLIENT_REGISTERED";
	public static final String USER_REGISTERED = "USER_REGISTERED";
	public static final String USER_PASSWORD_CHANGED = "USER_$_CHANGED";
	public static final String USER_PASSWORD_RESET_DONE = "USER_$_RESET_DONE";
	public static final String USER_RESET_PASSWORD_REQUEST = "USER_RESET_$_REQUEST";
	public static final String USER_CODE_GENERATION = "USER_CODE_GENERATION";
	public static final String USER_OTP_GENERATE = "USER_OTP_GENERATE";
	public static final String USER_APP_REQUEST = "USER_APP_REQUEST";
	public static final String USER_APP_REQ_ACKNOWLEDGED = "USER_APP_REQ_ACKNOWLEDGED";
	public static final String USER_APP_REQ_APPROVED = "USER_APP_REQ_APPROVED";
	public static final String USER_APP_REQ_REJECTED = "USER_APP_REQ_REJECTED";

	public static String getEventName(String eventName, Object... args) {
		return StringFormatter.format(eventName, args);
	}

	private EventNames() {
	}
}
