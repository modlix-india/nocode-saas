package com.fincity.saas.notification.model.message;

import com.fincity.saas.notification.enums.NotificationChannelType;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class InAppMessage extends AbstractNotificationMessage {

	String image;

	@Override
	public NotificationChannelType getNotificationChannelType() {
		return NotificationChannelType.IN_APP;
	}
}
