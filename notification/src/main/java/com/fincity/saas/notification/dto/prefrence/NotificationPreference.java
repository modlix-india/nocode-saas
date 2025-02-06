package com.fincity.saas.notification.dto.prefrence;

import java.io.Serial;
import java.util.Map;

import org.jooq.types.ULong;

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
public abstract class NotificationPreference<T extends NotificationPreference<T>> extends AbstractUpdatableDTO<ULong, ULong> {

	@Serial
	private static final long serialVersionUID = 4007524811937317620L;

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

	public NotificationPreference<T> setDisabled(boolean disabled) {
		if (disabled)
			disableAll();
		this.isDisabled = disabled;
		return this;
	}

	public NotificationPreference<T> setEmailEnabled(boolean emailEnabled) {
		this.isEmailEnabled = emailEnabled;
		changeDisabled();
		return this;
	}

	public NotificationPreference<T> setInAppEnabled(boolean inAppEnabled) {
		this.isInAppEnabled = inAppEnabled;
		changeDisabled();
		return this;
	}

	public NotificationPreference<T> setPushEnabled(boolean pushEnabled) {
		this.isPushEnabled = pushEnabled;
		changeDisabled();
		return this;
	}

	public NotificationPreference<T> setSmsEnabled(boolean smsEnabled) {
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
