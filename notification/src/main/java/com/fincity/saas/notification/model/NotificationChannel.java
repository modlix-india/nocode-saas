package com.fincity.saas.notification.model;

import com.fincity.saas.notification.dto.prefrence.UserPreference;
import com.fincity.saas.notification.enums.NotificationChannelType;
import com.fincity.saas.notification.model.message.EmailMessage;
import com.fincity.saas.notification.model.message.InAppMessage;
import com.fincity.saas.notification.model.message.NotificationMessage;
import com.fincity.saas.notification.model.message.SmsMessage;

import lombok.Getter;

@Getter
public class NotificationChannel {

	private boolean notificationEnabled;
	private EmailMessage email;
	private InAppMessage inApp;
	private SmsMessage sms;

	private NotificationChannel() {
		// user builder to create NotificationChannel
	}

	public static NotificationChannelBuilder builder() {
		return new NotificationChannelBuilder();
	}

	public static class NotificationChannelBuilder {

		private UserPreference preference;
		private boolean notificationEnabled;
		private EmailMessage email = null;
		private InAppMessage inApp = null;
		private SmsMessage sms = null;

		public NotificationChannelBuilder preferences(UserPreference preferences) {
			this.preference = preferences;
			this.notificationEnabled = !preferences.get(NotificationChannelType.DISABLED);
			return this;
		}

		public <T extends NotificationMessage> NotificationChannelBuilder addMessage(T message) {
			if (preference == null || !this.notificationEnabled)
				return this;

			if (Boolean.TRUE.equals(preference.getEmail() && message.getChannelType().equals(NotificationChannelType.EMAIL))
					&& message instanceof EmailMessage emailMessage)
				this.email = emailMessage;

			if (Boolean.TRUE.equals(preference.getInApp() && message.getChannelType().equals(NotificationChannelType.IN_APP))
					&& message instanceof InAppMessage inAppMessage)
				this.inApp = inAppMessage;

			if (Boolean.TRUE.equals(preference.getSms() && message.getChannelType().equals(NotificationChannelType.SMS))
					&& message instanceof SmsMessage smsMessage)
				this.sms = smsMessage;

			this.notificationEnabled = this.email != null || this.inApp != null;
			return this;
		}

		public NotificationChannel build() {
			NotificationChannel channel = new NotificationChannel();
			channel.notificationEnabled = this.notificationEnabled;
			channel.email = this.email;
			channel.inApp = this.inApp;
			channel.sms = this.sms;
			return channel;
		}
	}
}
