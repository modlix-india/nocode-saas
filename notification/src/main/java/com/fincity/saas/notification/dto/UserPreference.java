package com.fincity.saas.notification.dto;

import java.io.Serial;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.jooq.types.ULong;

import com.fincity.saas.commons.model.dto.AbstractUpdatableDTO;
import com.fincity.saas.commons.util.UniqueUtil;
import com.fincity.saas.notification.enums.NotificationChannelType;
import com.fincity.saas.notification.enums.PreferenceLevel;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;

@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@ToString(callSuper = true)
public class UserPreference extends AbstractUpdatableDTO<ULong, ULong> {

	private static final Map<String, Set<String>> DEFAULT_PREF = Arrays
			.stream(PreferenceLevel.values())
			.collect(Collectors.toMap(
					PreferenceLevel::getLiteral,
					PreferenceLevel::getDefaultList));

	@Serial
	private static final long serialVersionUID = 6629796623611093778L;

	private ULong appId;
	private ULong userId;
	private String code = UniqueUtil.shortUUID();
	private boolean enabled;

	private Map<String, Set<String>> preferences;

	public UserPreference setEnabled(boolean enabled) {
		if (!enabled)
			this.init();

		this.enabled = Boolean.TRUE;
		return this;
	}

	public UserPreference setPreferences(Map<String, Set<String>> preferences) {

		this.initDefault();

		if (preferences == null || preferences.isEmpty())
			return this;

		preferences.forEach((key, value) -> {
			if (value != null && !value.isEmpty())
				this.preferences.put(key, PreferenceLevel.lookupLiteral(key).toValidList(value));

		});

		this.enabled = this.hasAnyPreference();

		return this;
	}

	public boolean hasPreference(String pref) {
		return preferences.entrySet().stream()
				.anyMatch(entry -> entry.getValue().contains(pref)
						&& !PreferenceLevel.lookupLiteral(entry.getKey()).isReverseSave());
	}

	public boolean hasPreference(NotificationChannelType channelType) {
		Set<String> channelPreferences = this.preferences.get(PreferenceLevel.CHANNEL.getLiteral());
		if (channelPreferences == null)
			return false;
		return channelPreferences.contains(channelType.getLiteral());
	}

	public boolean hasAnyPreference() {

		if (this.preferences == null || this.preferences.isEmpty())
			return false;

		return preferences.entrySet().stream()
				.anyMatch(entry -> entry.getValue() != null &&
						!entry.getValue().isEmpty() &&
						!PreferenceLevel.lookupLiteral(entry.getKey()).getDefaultList().equals(entry.getValue()));
	}

	public void initDefault() {
		this.preferences = DEFAULT_PREF;
		this.enabled = Boolean.FALSE;
	}

	public void init() {
		this.setPreferences(this.preferences);
	}
}
