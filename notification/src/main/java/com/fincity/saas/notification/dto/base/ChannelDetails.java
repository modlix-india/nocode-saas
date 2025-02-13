package com.fincity.saas.notification.dto.base;

import com.fincity.saas.notification.enums.NotificationChannelType;

public interface ChannelDetails<V, T extends ChannelDetails<V, T>> {

	T setChannelValue(NotificationChannelType channelType, V value);

	V getChannelValue(NotificationChannelType channelType);

	default V getDisabled() {
		return this.getChannelValue(NotificationChannelType.DISABLED);
	}

	default T setDisabled(V value) {
		return this.setChannelValue(NotificationChannelType.DISABLED, value);
	}

	default V getEmail() {
		return this.getChannelValue(NotificationChannelType.EMAIL);
	}

	default T setEmail(V value) {
		return this.setChannelValue(NotificationChannelType.EMAIL, value);
	}

	default V getInApp() {
		return this.getChannelValue(NotificationChannelType.IN_APP);
	}

	default T setInApp(V value) {
		return this.setChannelValue(NotificationChannelType.IN_APP, value);
	}

	default V getMobilePush() {
		return this.getChannelValue(NotificationChannelType.MOBILE_PUSH);
	}

	default T setMobilePush(V value) {
		return this.setChannelValue(NotificationChannelType.MOBILE_PUSH, value);
	}

	default V getWebPush() {
		return this.getChannelValue(NotificationChannelType.WEB_PUSH);
	}

	default T setWebPush(V value) {
		return this.setChannelValue(NotificationChannelType.WEB_PUSH, value);
	}

	default T setSms(V value) {
		return this.setChannelValue(NotificationChannelType.SMS, value);
	}

	// TODO : Add channels if necessary
}
