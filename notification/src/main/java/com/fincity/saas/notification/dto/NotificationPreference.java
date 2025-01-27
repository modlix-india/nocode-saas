package com.fincity.saas.notification.dto;

import java.util.EnumMap;
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
	private boolean isEnabled;
	private boolean isEmailEnabled;
	private boolean isInAppEnabled;
	private boolean isSmsEnabled;
	private boolean isPushEnabled;

	public Map<NotificationChannelType, Boolean> getPreferences() {
		Map<NotificationChannelType, Boolean> preferences = new EnumMap<>(NotificationChannelType.class);
		preferences.put(NotificationChannelType.EMAIL, this.isEmailEnabled);
		preferences.put(NotificationChannelType.IN_APP, this.isInAppEnabled);
		preferences.put(NotificationChannelType.SMS, this.isSmsEnabled);
		preferences.put(NotificationChannelType.PUSH, this.isPushEnabled);
		return preferences;
	}

	public boolean getPreference(NotificationChannelType notificationChannelType) {
		return notificationChannelType != null ?
				this.getPreferences().getOrDefault(notificationChannelType, Boolean.FALSE) :
				Boolean.FALSE;
	}
}
