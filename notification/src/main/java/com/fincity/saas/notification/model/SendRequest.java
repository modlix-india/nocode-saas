package com.fincity.saas.notification.model;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigInteger;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.util.UniqueUtil;
import com.fincity.saas.notification.enums.NotificationChannelType;
import com.fincity.saas.notification.enums.NotificationType;
import com.fincity.saas.notification.model.NotificationChannel.NotificationChannelBuilder;
import com.fincity.saas.notification.model.message.NotificationMessage;
import com.fincity.saas.notification.model.response.NotificationErrorInfo;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@NoArgsConstructor
public class SendRequest implements Serializable {

	@Serial
	private static final long serialVersionUID = 8159104566559958425L;

	private String xDebug;
	private String code;
	private String appCode;
	private String clientCode;
	private BigInteger userId;
	private NotificationType notificationType;
	private Map<String, String> connections;
	private NotificationChannel channels;
	private NotificationErrorInfo errorInfo;

	public static SendRequest of(String appCode, String clientCode, BigInteger userId, String notificationType,
	                             Map<String, String> connections, NotificationChannel channels) {
		return new SendRequest()
				.setCode(UniqueUtil.shortUUID())
				.setAppCode(appCode)
				.setClientCode(clientCode)
				.setUserId(userId)
				.setNotificationType(NotificationType.lookupLiteral(notificationType))
				.setConnections(connections)
				.setChannels(channels);
	}

	public static SendRequest ofError(String appCode, String clientCode, BigInteger userId, String notificationType,
	                                  GenericException errorInfo) {
		return new SendRequest()
				.setCode(UniqueUtil.shortUUID())
				.setAppCode(appCode)
				.setClientCode(clientCode)
				.setUserId(userId)
				.setNotificationType(NotificationType.lookupLiteral(notificationType))
				.setErrorInfo(errorInfo);
	}

	public static SendRequest of(String appCode, String clientCode, BigInteger userId, String notificationType) {
		return of(appCode, clientCode, userId, notificationType, Map.of(),
				new NotificationChannelBuilder().isEnabled(Boolean.FALSE).build());
	}

	@JsonIgnore
	public boolean isValid(NotificationChannelType channelType) {
		return this.connections != null && this.channels != null &&
				this.connections.containsKey(channelType.getLiteral()) && this.channels.containsChannel(channelType);
	}

	@JsonIgnore
	public boolean isEmpty() {
		if (this.channels == null)
			return true;

		return !this.channels.containsAnyChannel();
	}

	public <T extends GenericException> SendRequest setErrorInfo(T exception) {
		this.errorInfo = new NotificationErrorInfo(exception);
		return this;
	}

	@JsonIgnore
	public boolean isError() {
		return this.errorInfo != null;
	}

	public <T extends NotificationMessage<T>> T getChannel(NotificationChannelType channelType) {
		return switch (channelType) {
			case DISABLED, WEB_PUSH, MOBILE_PUSH, SMS -> null;
			case EMAIL -> this.channels.getEmail();
			case IN_APP -> this.channels.getInApp();
		};
	}

}
