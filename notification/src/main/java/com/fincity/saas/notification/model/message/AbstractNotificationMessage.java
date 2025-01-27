package com.fincity.saas.notification.model.message;

import com.fincity.saas.notification.enums.NotificationChannelType;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public abstract class AbstractNotificationMessage {

	private String subject;
	private String body;

	public abstract NotificationChannelType getNotificationChannelType();
}
