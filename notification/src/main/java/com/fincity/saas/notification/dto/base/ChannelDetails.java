package com.fincity.saas.notification.dto.base;

import com.fincity.saas.notification.enums.NotificationChannelType;

public interface ChannelDetails<V, T extends ChannelDetails<V, T>> {

	T setChannelValue(NotificationChannelType channelType, V value);

	V get(NotificationChannelType channelType);

	default V getDisabled() {
		return this.get(NotificationChannelType.DISABLED);
	}

	default T setDisabled(V value) {
		return this.setChannelValue(NotificationChannelType.DISABLED, value);
	}

	default V getEmail() {
		return this.get(NotificationChannelType.EMAIL);
	}

	default T setEmail(V value) {
		return this.setChannelValue(NotificationChannelType.EMAIL, value);
	}

	default V getInApp() {
		return this.get(NotificationChannelType.IN_APP);
	}

	default T setInApp(V value) {
		return this.setChannelValue(NotificationChannelType.IN_APP, value);
	}

	default V getMobilePush() {
		return this.get(NotificationChannelType.MOBILE_PUSH);
	}

	default T setMobilePush(V value) {
		return this.setChannelValue(NotificationChannelType.MOBILE_PUSH, value);
	}

	default V getWebPush() {
		return this.get(NotificationChannelType.WEB_PUSH);
	}

	default T setWebPush(V value) {
		return this.setChannelValue(NotificationChannelType.WEB_PUSH, value);
	}

	default V getSms() {
		return this.get(NotificationChannelType.SMS);
	}

	default T setSms(V value) {
		return this.setChannelValue(NotificationChannelType.SMS, value);
	}

	// TODO : Add channels if necessary
}
