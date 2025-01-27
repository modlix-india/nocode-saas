package com.fincity.saas.notification.model.channel;

import com.fincity.saas.notification.enums.NotificationChannel;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class InAppMessage extends AbstractNotificationChannel {

	String image;

	@Override
	public NotificationChannel getNotificationChannel() {
		return NotificationChannel.IN_APP;
	}
}
