package com.fincity.saas.notification.enums;

import java.util.Set;

import org.jooq.EnumType;

import com.fincity.saas.commons.jooq.enums.notification.NotificationRecipientType;
import com.fincity.saas.notification.service.NotificationMessageResourceService;

import lombok.Getter;

@Getter
public enum NotificationChannelType implements EnumType {

	DISABLED("DISABLED"),
	EMAIL("EMAIL", NotificationRecipientType.FROM, NotificationRecipientType.TO, NotificationRecipientType.BCC,
			NotificationRecipientType.CC, NotificationRecipientType.REPLY_TO),
	IN_APP("IN_APP"),
	MOBILE_PUSH("MOBILE_PUSH"),
	WEB_PUSH("WEB_PUSH"),
	SMS("SMS", NotificationRecipientType.TO);

	private final String literal;

	private final Set<NotificationRecipientType> allowedRecipientTypes;

	NotificationChannelType(String literal, NotificationRecipientType... notificationRecipientTypes) {
		this.literal = literal;
		this.allowedRecipientTypes = notificationRecipientTypes == null ? Set.of() : Set.of(notificationRecipientTypes);
	}

	public static NotificationChannelType lookupLiteral(String literal) {
		return EnumType.lookupLiteral(NotificationChannelType.class, literal);
	}

	public static NotificationChannelType getFromConnectionSubType(String connectionSubType) {

		if (!connectionSubType.startsWith(NotificationMessageResourceService.NOTIFICATION_PREFIX))
			return null;

		connectionSubType = connectionSubType.substring(NotificationMessageResourceService.NOTIFICATION_PREFIX.length());

		return NotificationChannelType.valueOf(connectionSubType.split("_")[0].toUpperCase());
	}

	@Override
	public String getLiteral() {
		return literal;
	}

	@Override
	public String getName() {
		return null;
	}

	public String getQueueName(String exchangeName) {
		return exchangeName + "." + this.getLiteral().toLowerCase();
	}

	public boolean hasConnectionSubType(NotificationRecipientType recipientType) {
		return this.allowedRecipientTypes.contains(recipientType);
	}
}
