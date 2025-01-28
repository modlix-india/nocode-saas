package com.fincity.saas.notification.model;

import com.fincity.saas.notification.dto.NotificationPreference;
import com.fincity.saas.notification.enums.NotificationChannelType;
import com.fincity.saas.notification.model.message.AbstractNotificationMessage;
import com.fincity.saas.notification.model.message.EmailMessage;
import com.fincity.saas.notification.model.message.InAppMessage;

import lombok.Getter;

@Getter
public class NotificationChannel {

	private boolean notificationEnabled;
	private EmailMessage email;
	private InAppMessage inApp;

	private NotificationChannel() {
	}

	public static NotificationChannelBuilder builder() {
		return new NotificationChannelBuilder();
	}

	public static class NotificationChannelBuilder {

		private NotificationPreference preference;
		private boolean notificationEnabled;
		private EmailMessage email = null;
		private InAppMessage inApp = null;

		public NotificationChannelBuilder preferences(NotificationPreference preferences) {
			this.preference = preferences;
			this.notificationEnabled = !preferences.has(NotificationChannelType.DISABLED);
			return this;
		}

		public <T extends AbstractNotificationMessage> NotificationChannelBuilder addMessageInfo(T message) {
			if (preference == null || !this.notificationEnabled)
				return this;

			if (preference.has(NotificationChannelType.EMAIL)
					&& message.getNotificationChannelType().equals(NotificationChannelType.EMAIL)
					&& message instanceof EmailMessage emailMessage)
				this.email = emailMessage;

			if (preference.has(NotificationChannelType.IN_APP)
					&& message.getNotificationChannelType().equals(NotificationChannelType.IN_APP)
					&& message instanceof InAppMessage inAppMessage)
				this.inApp = inAppMessage;

			this.notificationEnabled = this.email != null || this.inApp != null;
			return this;
		}

		public NotificationChannel build() {
			NotificationChannel channel = new NotificationChannel();
			channel.notificationEnabled = this.notificationEnabled;
			channel.email = this.email;
			channel.inApp = this.inApp;
			return channel;
		}
	}
}
