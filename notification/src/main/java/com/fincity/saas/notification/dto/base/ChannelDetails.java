package com.fincity.saas.notification.dto.base;

import com.fincity.saas.notification.enums.NotificationChannelType;

public interface ChannelDetails<V, T extends ChannelDetails<V, T>> {

	T setChannelValue(NotificationChannelType channelType, V value);

	V getChannelValue(NotificationChannelType channelType);

	default V getDisabled() {
		return getChannelValue(NotificationChannelType.DISABLED);
	}

	default T setDisabled(V value) {
		return setChannelValue(NotificationChannelType.DISABLED, value);
	}

	default V getEmail() {
		return getChannelValue(NotificationChannelType.EMAIL);
	}

	default T setEmail(V value) {
		return setChannelValue(NotificationChannelType.EMAIL, value);
	}

	default V getInApp() {
		return getChannelValue(NotificationChannelType.IN_APP);
	}

	default T setInApp(V value) {
		return setChannelValue(NotificationChannelType.IN_APP, value);
	}

	default V getMobilePush() {
		return getChannelValue(NotificationChannelType.MOBILE_PUSH);
	}

	default T setMobilePush(V value) {
		return setChannelValue(NotificationChannelType.MOBILE_PUSH, value);
	}

	default V getWebPush() {
		return getChannelValue(NotificationChannelType.WEB_PUSH);
	}

	default T setWebPush(V value) {
		return setChannelValue(NotificationChannelType.WEB_PUSH, value);
	}

	default T setSms(V value) {
		return setChannelValue(NotificationChannelType.SMS, value);
	}

	// TODO : Add channels if necessary
}
