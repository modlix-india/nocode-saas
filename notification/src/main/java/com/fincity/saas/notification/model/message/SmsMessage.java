package com.fincity.saas.notification.model.message;

import com.fincity.saas.notification.enums.NotificationChannelType;

public class SmsMessage extends NotificationMessage {

	@Override
	public NotificationChannelType getChannelType() {
		return NotificationChannelType.SMS;
	}
}
