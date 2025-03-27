package com.fincity.saas.notification.enums.channel.inapp;

import org.jooq.EnumType;

import lombok.Getter;

@Getter
public enum ActionType implements EnumType {

	CLICKABLE("CLICKABLE"),
	REDIRECT("REDIRECT"),
	OPEN_MODAL("OPEN_MODAL"),
	ACCEPT_REJECT("ACCEPT_REJECT"),
	CONFIRM_DISMISS("CONFIRM_DISMISS"),
	REPLY("REPLY"),
	REACT("REACT"),

	//TODO: We can implement these actions in future
	AUTO_DISMISS("AUTO_DISMISS"),
	SNOOZE("SNOOZE"),
	SCHEDULE("SCHEDULE");

	private final String literal;

	ActionType(String literal) {
		this.literal = literal;
	}

	public static ActionType lookupLiteral(String literal) {
		return EnumType.lookupLiteral(ActionType.class, literal);
	}

	@Override
	public String getLiteral() {
		return this.literal;
	}

	@Override
	public String getName() {
		return null;
	}
}
