package com.fincity.saas.core.enums;

public enum EventActionTaskType {

	SEND_EMAIL("Send Email"),

	CALL_COREFUNCTION("Call Core Function"),

	;

	private String displayName;

	private EventActionTaskType(String displayName) {
		this.displayName = displayName;
	}

	public String getDisplayName() {
		return displayName;
	}
}
