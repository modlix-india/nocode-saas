package com.fincity.saas.notification.dto.preference;

import java.io.Serial;

import com.fincity.saas.notification.enums.NotificationChannelType;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;

@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@ToString(callSuper = true)
public class UserChannelPref extends UserPref<NotificationChannelType, UserChannelPref> {

	@Serial
	private static final long serialVersionUID = 730683392131950303L;

	private NotificationChannelType channelType;

	@Override
	public NotificationChannelType getValue() {
		return this.getChannelType();
	}

	@Override
	public UserPref<NotificationChannelType, UserChannelPref> setValue(NotificationChannelType value) {
		return this.setChannelType(value);
	}
}
