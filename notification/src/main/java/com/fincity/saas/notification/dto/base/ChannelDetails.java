package com.fincity.saas.notification.dto.base;

import com.fincity.saas.notification.enums.NotificationChannelType;

public interface ChannelDetails<V, T extends ChannelDetails<V, T>> {

	T setChannelValue(NotificationChannelType channelType, V value);

	default T setDisabled(V value) {
		return setChannelValue(NotificationChannelType.DISABLED, value);
	}

	default T setEmail(V value) {
		return setChannelValue(NotificationChannelType.EMAIL, value);
	}

	default T setInApp(V value) {
		return setChannelValue(NotificationChannelType.IN_APP, value);
	}

	default T setMobilePush(V value) {
		return setChannelValue(NotificationChannelType.MOBILE_PUSH, value);
	}

	default T setWebPush(V value) {
		return setChannelValue(NotificationChannelType.WEB_PUSH, value);
	}

	default T setSms(V value) {
		return setChannelValue(NotificationChannelType.SMS, value);
	}

	// TODO : Add channels if necessary
}
