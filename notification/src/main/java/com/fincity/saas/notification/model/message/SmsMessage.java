package com.fincity.saas.notification.model.message;

import com.fincity.saas.notification.enums.NotificationChannelType;

public class SmsMessage extends AbstractNotificationMessage {

	@Override
	public NotificationChannelType getNotificationChannelType() {
		return NotificationChannelType.SMS;
	}
}
