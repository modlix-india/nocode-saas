package com.fincity.saas.commons.core.enums.notification;

import org.jooq.EnumType;

import lombok.Getter;

@Getter
public enum NotificationRecipientType implements EnumType {

	FROM("FROM", "From"),
	TO("TO", "To"),
	CC("CC", "cc"),
	BCC("BCC", "Bcc"),
	REPLY_TO("REPLY_TO", "Reply To"),
	PHONE_NUMBER("PHONE_NUMBER", "Phone Number");

	private final String literal;

	private final String displayName;

	NotificationRecipientType(String literal, String displayName) {
		this.literal = literal;
		this.displayName = displayName;
	}

	public static NotificationRecipientType lookupLiteral(String literal) {
		return EnumType.lookupLiteral(NotificationRecipientType.class, literal);
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
