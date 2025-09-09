package com.fincity.saas.notification.enums.channel;

public interface ChannelType {

    default NotificationChannelType getChannelType() {
        return NotificationChannelType.DISABLED;
    }
}
