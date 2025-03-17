package com.fincity.saas.notification.model;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fincity.saas.notification.dto.UserPreference;
import com.fincity.saas.notification.enums.ChannelType;
import com.fincity.saas.notification.enums.NotificationChannelType;
import com.fincity.saas.notification.model.message.EmailMessage;
import com.fincity.saas.notification.model.message.InAppMessage;
import com.fincity.saas.notification.model.message.NotificationMessage;
import com.fincity.saas.notification.model.message.RecipientInfo;
import com.fincity.saas.notification.model.message.SmsMessage;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

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

	@JsonIgnore
	public boolean containsAnyChannel() {
		return email != null || inApp != null || sms != null;
	}

	@JsonIgnore
	public boolean containsChannel(NotificationChannelType channelType) {
		return switch (channelType) {
			case EMAIL -> email != null && !email.isNull();
			case IN_APP -> inApp != null && !inApp.isNull();
			case SMS -> sms != null && !sms.isNull();
			default -> false;
		};
	}

	public Map<String, Object> toMap() {
		Gson gson = new Gson();
		String json = gson.toJson(this);
		return gson.fromJson(json, new TypeToken<Map<String, Object>>() {
		}.getType());
	}

	public  List<NotificationChannelType> getEnabledChannels() {
		return Stream.of(this.email, this.inApp, this.sms)
				.filter(message -> message != null && !message.isNull())
				.map(ChannelType::getChannelType).toList();
	}

	public static class NotificationChannelBuilder {

		private UserPreference preference;
		private boolean notificationEnabled;
		private EmailMessage email = null;
		private InAppMessage inApp = null;
		private SmsMessage sms = null;

		public NotificationChannelBuilder preferences(UserPreference preferences) {
			this.preference = preferences;
			this.notificationEnabled = preferences.isEnabled();
			return this;
		}

		public NotificationChannelBuilder isEnabled(boolean isEnabled) {
			this.notificationEnabled = isEnabled;
			return this;
		}

		public <T extends NotificationMessage<T>> NotificationChannelBuilder addMessage(T message, RecipientInfo userInfo) {
			if (preference == null || !this.notificationEnabled)
				return this;

			if (preference.hasPreference(NotificationChannelType.EMAIL)
					&& message.getChannelType().equals(NotificationChannelType.EMAIL)
					&& message instanceof EmailMessage emailMessage)
				this.email = emailMessage.addRecipientInfo(userInfo);

			if (preference.hasPreference(NotificationChannelType.IN_APP)
					&& message.getChannelType().equals(NotificationChannelType.IN_APP)
					&& message instanceof InAppMessage inAppMessage)
				this.inApp = inAppMessage.addRecipientInfo(userInfo);

			if (preference.hasPreference(NotificationChannelType.SMS)
					&& message.getChannelType().equals(NotificationChannelType.SMS)
					&& message instanceof SmsMessage smsMessage)
				this.sms = smsMessage.addRecipientInfo(userInfo);

			this.notificationEnabled = this.email != null || this.inApp != null || this.sms != null;
			return this;
		}

		public <T extends NotificationMessage<T>> NotificationChannelBuilder addMessage(T message) {
			if (preference == null || !this.notificationEnabled)
				return this;

			if (preference.hasPreference(NotificationChannelType.EMAIL)
					&& message.getChannelType().equals(NotificationChannelType.EMAIL)
					&& message instanceof EmailMessage emailMessage)
				this.email = emailMessage;

			if (preference.hasPreference(NotificationChannelType.IN_APP)
					&& message.getChannelType().equals(NotificationChannelType.IN_APP)
					&& message instanceof InAppMessage inAppMessage)
				this.inApp = inAppMessage;

			if (preference.hasPreference(NotificationChannelType.SMS)
					&& message.getChannelType().equals(NotificationChannelType.SMS)
					&& message instanceof SmsMessage smsMessage)
				this.sms = smsMessage;

			this.notificationEnabled = this.email != null || this.inApp != null || this.sms != null;
			return this;
		}

		public NotificationChannelBuilder addUserInfo(RecipientInfo userInfo) {
			if (preference == null || !this.notificationEnabled)
				return this;

			if (email != null)
				email.addRecipientInfo(userInfo);

			if (inApp != null)
				inApp.addRecipientInfo(userInfo);

			if (sms != null)
				sms.addRecipientInfo(userInfo);

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
