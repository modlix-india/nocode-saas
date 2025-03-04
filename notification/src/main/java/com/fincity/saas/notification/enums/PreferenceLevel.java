package com.fincity.saas.notification.enums;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.UnaryOperator;

import org.jooq.EnumType;

import lombok.Getter;

@Getter
public enum PreferenceLevel implements EnumType {

	CHANNEL("CHANNEL", "Channel", Boolean.FALSE,
			Set.of(NotificationChannelType.DISABLED.getLiteral()), PreferenceLevel::toValidChannelList),
	NOTIFICATION("NOTIFICATION", "Disabled Notification", Boolean.TRUE,
			Set.of(), PreferenceLevel::toValidNotificationMap);

	private final String literal;
	private final String displayName;
	private final boolean reverseSave;
	private final Set<String> defaultList;
	private final UnaryOperator<Set<String>> validator;

	PreferenceLevel(String literal, String displayName, Boolean reverseSave, Set<String> defaultList,
	                UnaryOperator<Set<String>> validator) {
		this.literal = literal;
		this.displayName = displayName;
		this.reverseSave = reverseSave;
		this.defaultList = new HashSet<>(defaultList);
		this.validator = validator;
	}

	public static PreferenceLevel lookupLiteral(String literal) {
		return EnumType.lookupLiteral(PreferenceLevel.class, literal);
	}

	private static Set<String> toValidChannelList(Set<String> preferences) {

		if (preferences.contains(NotificationChannelType.DISABLED.getLiteral())) {
			if (preferences.size() > 1) {
				throw new IllegalArgumentException("Invalid channel preferences, disabled channel can not be set with other channels");
			}
			return new HashSet<>(List.of(NotificationChannelType.DISABLED.getLiteral()));
		}

		return preferences;
	}

	private static Set<String> toValidNotificationMap(Set<String> preferences) {
		return preferences;
	}

	@Override
	public String getLiteral() {
		return this.literal;
	}

	@Override
	public String getName() {
		return this.name();
	}

	public Set<String> getDefaultList() {
		return new HashSet<>(this.defaultList);
	}

	public Set<String> toValidList(Collection<String> preferences) {

		if (preferences == null || preferences.isEmpty())
			return this.defaultList;

		return this.validator.apply(new HashSet<>(preferences));
	}
}
