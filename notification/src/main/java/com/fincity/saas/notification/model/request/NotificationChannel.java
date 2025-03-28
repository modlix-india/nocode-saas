package com.fincity.saas.notification.model.request;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.stream.Stream;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fincity.saas.notification.dto.UserPreference;
import com.fincity.saas.notification.enums.channel.ChannelType;
import com.fincity.saas.notification.enums.channel.NotificationChannelType;
import com.fincity.saas.notification.model.message.NotificationMessage;
import com.fincity.saas.notification.model.message.recipient.RecipientInfo;
import com.fincity.saas.notification.model.message.channel.EmailMessage;
import com.fincity.saas.notification.model.message.channel.InAppMessage;
import com.fincity.saas.notification.model.message.channel.MobilePushMessage;
import com.fincity.saas.notification.model.message.channel.SmsMessage;
import com.fincity.saas.notification.model.message.channel.WebPushMessage;

import lombok.Getter;

@Getter
public class NotificationChannel implements Serializable {

	@Serial
	private static final long serialVersionUID = 5676431093000206490L;

	private boolean notificationEnabled;
	private EmailMessage email;
	private InAppMessage inApp;
	private MobilePushMessage mobilePush;
	private WebPushMessage webPush;
	private SmsMessage sms;

	private NotificationChannel() {
		// user builder to create NotificationChannel
	}

	public static NotificationChannelBuilder builder() {
		return new NotificationChannelBuilder();
	}

	@JsonIgnore
	public boolean containsAnyChannel() {
		return email != null || inApp != null || mobilePush != null || webPush != null || sms != null;
	}

	@JsonIgnore
	public boolean containsChannel(NotificationChannelType channelType) {
		return switch (channelType) {
			case EMAIL -> email != null && !email.isNull();
			case IN_APP -> inApp != null && !inApp.isNull();
			case MOBILE_PUSH -> mobilePush != null && !mobilePush.isNull();
			case WEB_PUSH -> webPush != null && !webPush.isNull();
			case SMS -> sms != null && !sms.isNull();
			default -> false;
		};
	}

	@JsonIgnore
	@SuppressWarnings("unchecked")
	public <T extends NotificationMessage<T>> T get(NotificationChannelType channelType) {
		return switch (channelType) {
			case EMAIL -> (T) email;
			case IN_APP -> (T) inApp;
			case MOBILE_PUSH -> (T) mobilePush;
			case WEB_PUSH -> (T) webPush;
			case SMS -> (T) sms;
			default -> null;
		};
	}

	@JsonIgnore
	public List<NotificationChannelType> getEnabledChannels() {
		return Stream.of(this.email, this.inApp, this.mobilePush, this.webPush, this.sms)
				.filter(message -> message != null && !message.isNull())
				.map(ChannelType::getChannelType).toList();
	}

	public static class NotificationChannelBuilder {

		private UserPreference preference;
		private boolean notificationEnabled;
		private EmailMessage email = null;
		private InAppMessage inApp = null;
		private MobilePushMessage mobilePush = null;
		private WebPushMessage webPush = null;
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

			if (preference.hasPreference(NotificationChannelType.MOBILE_PUSH)
					&& message.getChannelType().equals(NotificationChannelType.MOBILE_PUSH)
					&& message instanceof MobilePushMessage mobilePushMessage)
				this.mobilePush = mobilePushMessage;

			if (preference.hasPreference(NotificationChannelType.WEB_PUSH)
					&& message.getChannelType().equals(NotificationChannelType.WEB_PUSH)
					&& message instanceof WebPushMessage webPushMessage)
				this.webPush = webPushMessage;

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

			if (mobilePush != null)
				mobilePush.addRecipientInfo(userInfo);

			if (webPush != null)
				webPush.addRecipientInfo(userInfo);

			if (sms != null)
				sms.addRecipientInfo(userInfo);

			return this;
		}

		public NotificationChannel build() {
			NotificationChannel channel = new NotificationChannel();
			channel.notificationEnabled = this.notificationEnabled;
			channel.email = this.email;
			channel.inApp = this.inApp;
			channel.mobilePush = this.mobilePush;
			channel.webPush = this.webPush;
			channel.sms = this.sms;
			return channel;
		}
	}
}
