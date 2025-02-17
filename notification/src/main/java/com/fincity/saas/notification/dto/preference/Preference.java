package com.fincity.saas.notification.dto.preference;

import java.util.HashSet;
import java.util.Set;

import org.jooq.types.ULong;

import lombok.Data;
import lombok.ToString;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@ToString(callSuper = true)
public class Preference<T> {

	private ULong appId;
	private ULong userId;
	private Set<String> codes;
	private boolean containsOnlyDisable;
	private Set<T> preferences;
	private Set<T> disabledPreferences;

	public void addCode(String code) {

		if (this.codes == null)
			this.codes = new HashSet<>();

		this.codes.add(code);
	}

	public void addPreference(T preference) {

		if (this.preferences == null)
			this.preferences = new HashSet<>();

		this.preferences.add(preference);
	}

	public void addDisabledPreference(T preference) {
		if (this.disabledPreferences == null)
			this.disabledPreferences = new HashSet<>();

		this.disabledPreferences.add(preference);
	}

	public boolean hasPreference(T preference) {

		if (this.containsOnlyDisable)
			return this.disabledPreferences == null || !disabledPreferences.contains(preference);

		if (this.disabledPreferences != null && this.disabledPreferences.contains(preference))
			return false;

		return this.preferences != null && this.preferences.contains(preference);
	}

	public boolean hasCode(String code) {
		return this.codes.contains(code);
	}
}
