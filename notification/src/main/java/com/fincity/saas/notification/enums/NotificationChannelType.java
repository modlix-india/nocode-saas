package com.fincity.saas.notification.enums;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.jooq.EnumType;

import com.fincity.saas.commons.jooq.enums.ConnectionSubType;
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

	public static NotificationChannelType getFromConnectionSubType(ConnectionSubType connectionSubType) {

		String name = connectionSubType.name();

		if (!name.startsWith(NotificationMessageResourceService.NOTIFICATION_PREFIX))
			return null;

		name = name.substring(NotificationMessageResourceService.NOTIFICATION_PREFIX.length());

		return NotificationChannelType.valueOf(name.split("_")[0].toUpperCase());
	}

	public static <T> Map<NotificationChannelType, T> getChannelTypeMap(Map<String, T> channelMap) {

		if (channelMap == null || channelMap.isEmpty())
			return new EnumMap<>(NotificationChannelType.class);

		return channelMap.entrySet()
				.stream()
				.collect(Collectors.toMap(e ->
								NotificationChannelType.lookupLiteral(e.getKey()),
						Map.Entry::getValue,
						(existing, replacement) -> replacement,
						() -> new EnumMap<>(NotificationChannelType.class)
				));

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

	public boolean hasRecipientType(NotificationRecipientType recipientType) {
		return this.allowedRecipientTypes.contains(recipientType);
	}
}
