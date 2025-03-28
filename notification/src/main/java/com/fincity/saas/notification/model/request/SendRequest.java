package com.fincity.saas.notification.model.request;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.util.UniqueUtil;
import com.fincity.saas.notification.enums.NotificationType;
import com.fincity.saas.notification.enums.channel.NotificationChannelType;
import com.fincity.saas.notification.model.request.NotificationChannel.NotificationChannelBuilder;
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
	private NotificationType notificationType = NotificationType.INFO;
	private Map<String, String> connections;
	private NotificationChannel channels;
	private NotificationErrorInfo errorInfo;
	private Map<String, NotificationErrorInfo> channelErrors;

	private static SendRequest of(String appCode, String clientCode, BigInteger userId, String notificationType,
			Map<String, String> connections, NotificationChannel channels, NotificationErrorInfo errorInfo) {
		return new SendRequest().setCode(UniqueUtil.shortUUID()).setAppCode(appCode).setClientCode(clientCode)
				.setUserId(userId)
				.setNotificationType(notificationType != null ? NotificationType.lookupLiteral(notificationType)
						: NotificationType.INFO)
				.setConnections(connections).setChannels(channels).setErrorInfo(errorInfo);
	}

	public static SendRequest of(String appCode, String clientCode, BigInteger userId, String notificationType,
			Map<String, String> connections, NotificationChannel channels) {
		return of(appCode, clientCode, userId, notificationType, connections, channels, null);
	}

	public static SendRequest of(String appCode, String clientCode, BigInteger userId, String notificationType) {
		return of(appCode, clientCode, userId, notificationType, Map.of(),
				new NotificationChannelBuilder().isEnabled(Boolean.FALSE).build());
	}

	public static SendRequest ofError(String appCode, String clientCode, BigInteger userId, String notificationType,
			GenericException errorInfo) {
		return of(appCode, clientCode, userId, notificationType, null, null, new NotificationErrorInfo(errorInfo));
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

	public <T extends GenericException> SendRequest setChannelErrorInfo(T exception,
			NotificationChannelType channelType) {
		if (this.channelErrors == null)
			this.channelErrors = new HashMap<>();
		this.channelErrors.put(channelType.getLiteral(), new NotificationErrorInfo(exception));
		return this;
	}

	@JsonIgnore
	public boolean isError() {
		return this.errorInfo != null || (this.channelErrors != null && !this.channelErrors.isEmpty());
	}

}
