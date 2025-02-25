package com.fincity.saas.commons.jooq.enums.notification;

public interface ChannelType {

	default NotificationChannelType getChannelType() {
		return NotificationChannelType.DISABLED;
	}
}
