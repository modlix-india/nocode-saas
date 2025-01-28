package com.fincity.saas.notification.dto;

import java.util.Map;

import org.jooq.types.ULong;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fincity.saas.commons.model.dto.AbstractUpdatableDTO;
import com.fincity.saas.notification.enums.NotificationChannelType;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;

@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@ToString(callSuper = true)
public class NotificationPreference extends AbstractUpdatableDTO<ULong, ULong> {

	@JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
	private ULong appId;

	private ULong userId;
	private ULong notificationTypeId;

	private boolean isDisabled;
	private boolean isEmailEnabled;
	private boolean isInAppEnabled;
	private boolean isSmsEnabled;
	private boolean isPushEnabled;

	public Map<NotificationChannelType, Boolean> getPreferences() {
		return Map.of(
				NotificationChannelType.DISABLED, this.isDisabled,
				NotificationChannelType.EMAIL, this.isEmailEnabled,
				NotificationChannelType.IN_APP, this.isInAppEnabled,
				NotificationChannelType.SMS, this.isSmsEnabled,
				NotificationChannelType.PUSH, this.isPushEnabled
		);
	}

	public boolean has(NotificationChannelType notificationChannelType) {
		return switch (notificationChannelType) {
			case DISABLED -> this.isDisabled;
			case EMAIL -> this.isEmailEnabled;
			case IN_APP -> this.isInAppEnabled;
			case SMS -> this.isSmsEnabled;
			case PUSH -> this.isPushEnabled;
		};
	}

	public NotificationPreference setDisabled(boolean disabled) {
		if (disabled)
			disableAll();
		this.isDisabled = disabled;
		return this;
	}

	public NotificationPreference setEmailEnabled(boolean emailEnabled) {
		this.isEmailEnabled = emailEnabled;
		changeDisabled();
		return this;
	}

	public NotificationPreference setInAppEnabled(boolean inAppEnabled) {
		this.isInAppEnabled = inAppEnabled;
		changeDisabled();
		return this;
	}

	public NotificationPreference setPushEnabled(boolean pushEnabled) {
		this.isPushEnabled = pushEnabled;
		changeDisabled();
		return this;
	}

	public NotificationPreference setSmsEnabled(boolean smsEnabled) {
		this.isSmsEnabled = smsEnabled;
		changeDisabled();
		return this;
	}

	private void disableAll() {
		this.isEmailEnabled = false;
		this.isInAppEnabled = false;
		this.isSmsEnabled = false;
		this.isPushEnabled = false;
	}

	private void changeDisabled() {
		this.isDisabled = !this.isEmailEnabled && !this.isInAppEnabled && !this.isSmsEnabled && !this.isPushEnabled;
	}
}
