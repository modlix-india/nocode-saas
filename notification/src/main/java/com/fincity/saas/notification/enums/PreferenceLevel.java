package com.fincity.saas.notification.enums;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

import org.jooq.EnumType;

import lombok.Getter;

@Getter
public enum PreferenceLevel implements EnumType {

	CHANNEL("CHANNEL", "Channel", Boolean.FALSE),
	NOTIFICATION("NOTIFICATION", "Disabled Notification", Boolean.TRUE);

	private final String literal;
	private final String displayName;
	private final boolean allDisable;
	private final Map<String, Boolean> defaultMap;
	private final UnaryOperator<Map<String, Boolean>> validator;

	PreferenceLevel(String literal, String displayName, Boolean allDisable) {
		this.literal = literal;
		this.displayName = displayName;
		this.allDisable = allDisable;
		this.defaultMap = initilizeDefaultMap();
		this.validator = initializeValidator();
	}

	public static PreferenceLevel lookupLiteral(String literal) {
		return EnumType.lookupLiteral(PreferenceLevel.class, literal);
	}

	private Map<String, Boolean> initilizeDefaultMap() {
		return switch (this) {
			case CHANNEL -> Arrays.stream(NotificationChannelType.values())
					.collect(Collectors.toMap(
							NotificationChannelType::getLiteral,
							channelType -> channelType.equals(NotificationChannelType.DISABLED),
							(a, b) -> b,
							HashMap::new));
			case NOTIFICATION -> new HashMap<>();
		};
	}

	private UnaryOperator<Map<String, Boolean>> initializeValidator() {
		return switch (this) {
			case CHANNEL -> this::toValidChannelMap;
			case NOTIFICATION -> this::toValidNotificationMap;
		};
	}

	@Override
	public String getLiteral() {
		return this.literal;
	}

	@Override
	public String getName() {
		return this.name();
	}

	public Map<String, Boolean> toValidMap(Map<String, Boolean> preferenceMap) {

		if (preferenceMap == null || preferenceMap.isEmpty())
			return this.defaultMap;

		return this.validator.apply(preferenceMap);
	}

	private Map<String, Boolean> toValidChannelMap(Map<String, Boolean> preferenceMap) {
		boolean isDisabled = preferenceMap.getOrDefault(NotificationChannelType.DISABLED.getLiteral(), Boolean.FALSE);

		if (isDisabled)
			return this.defaultMap;

		for (NotificationChannelType channelType : NotificationChannelType.values()) {
			preferenceMap.putIfAbsent(channelType.getLiteral(), Boolean.FALSE);
		}

		return preferenceMap;
	}

	private Map<String, Boolean> toValidNotificationMap(Map<String, Boolean> preferenceMap) {
		this.defaultMap.forEach((key, value) -> preferenceMap.put(key, Boolean.FALSE));
		return preferenceMap;
	}
}
