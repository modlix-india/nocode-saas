package com.fincity.saas.notification.model.channel;

import com.fincity.saas.notification.enums.NotificationChannel;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public abstract class AbstractNotificationChannel {

	String subject;
	String body;

	public abstract NotificationChannel getNotificationChannel();
}
