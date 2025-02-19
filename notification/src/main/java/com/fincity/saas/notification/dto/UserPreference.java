package com.fincity.saas.notification.dto;

import java.io.Serial;
import java.util.Arrays;
import java.util.Map;
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

	private static final Map<String, Map<String, Boolean>> DEFAULT_PREF = Arrays
			.stream(PreferenceLevel.values())
			.collect(Collectors.toMap(
					PreferenceLevel::getLiteral,
					PreferenceLevel::getDefaultMap));

	@Serial
	private static final long serialVersionUID = 6629796623611093778L;

	private ULong appId;
	private ULong userId;
	private String code = UniqueUtil.shortUUID();
	private boolean enabled = Boolean.FALSE;

	private Map<String, Map<String, Boolean>> preferences;

	public UserPreference setEnabled(boolean enabled) {
		if (!enabled)
			this.preferences = DEFAULT_PREF;

		this.enabled = Boolean.TRUE;
		return this;
	}

	public UserPreference setPreference(Map<String, Map<String, Boolean>> preferences) {

		this.initDefault();

		if (preferences == null || preferences.isEmpty())
			return this;

		preferences.keySet()
				.forEach(level -> this.preferences.put(level,
						PreferenceLevel.lookupLiteral(level).toValidMap(this.preferences.get(level))));
		if (hasAnyPreference())
			this.enabled = Boolean.TRUE;
		return this;
	}

	public boolean hasPreference(String prefName) {

		for (Map.Entry<String, Map<String, Boolean>> entry : preferences.entrySet()) {
			if (entry.getValue().containsKey(prefName))
				return PreferenceLevel.lookupLiteral(entry.getKey()).isAllDisable() ? Boolean.FALSE : entry.getValue().get(prefName);
		}
		return Boolean.FALSE;

	}

	public boolean hasPreference(NotificationChannelType channelType) {
		return preferences.get(PreferenceLevel.CHANNEL.getLiteral()).getOrDefault(channelType.getLiteral(), Boolean.FALSE);
	}

	public boolean hasAnyPreference() {

		for (Map.Entry<String, Map<String, Boolean>> entry : preferences.entrySet()) {
			if (!entry.getValue().isEmpty()) {
				return PreferenceLevel.lookupLiteral(entry.getKey()).isAllDisable() ? Boolean.TRUE
						: entry.getValue().values().stream().anyMatch(Boolean.TRUE::equals);
			}
		}
		return Boolean.FALSE;
	}

	public void initDefault() {
		this.preferences = DEFAULT_PREF;
		this.enabled = Boolean.FALSE;
	}
}
