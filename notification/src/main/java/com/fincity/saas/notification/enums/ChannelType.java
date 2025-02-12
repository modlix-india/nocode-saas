package com.fincity.saas.notification.enums;

public interface ChannelType {

	default NotificationChannelType getChannelType() {
		return NotificationChannelType.DISABLED;
	}
}
