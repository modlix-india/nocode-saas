package com.fincity.saas.notification.model;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigInteger;
import java.util.Map;

import com.fincity.saas.commons.util.UniqueUtil;
import com.fincity.saas.notification.enums.NotificationChannelType;
import com.fincity.saas.notification.enums.NotificationType;
import com.fincity.saas.notification.model.NotificationChannel.NotificationChannelBuilder;

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
	private Map<NotificationChannelType, String> connections;
	private NotificationChannel channels;

	public static SendRequest of(String appCode, String clientCode, BigInteger userId, String notificationType,
	                             Map<NotificationChannelType, String> connections, NotificationChannel channels) {
		return new SendRequest()
				.setCode(UniqueUtil.shortUUID())
				.setAppCode(appCode)
				.setClientCode(clientCode)
				.setUserId(userId)
				.setNotificationType(NotificationType.lookupLiteral(notificationType))
				.setConnections(connections)
				.setChannels(channels);
	}

	public static SendRequest of(String appCode, String clientCode, BigInteger userId, String notificationType) {
		return of(appCode, clientCode, userId, notificationType, Map.of(),
				new NotificationChannelBuilder().isEnabled(Boolean.FALSE).build());
	}

	public boolean isValid(NotificationChannelType channelType) {
		return this.connections != null && this.channels != null &&
				this.connections.containsKey(channelType) && this.channels.containsChannel(channelType);
	}

	public boolean isEmpty() {
		return this.channels == null || !this.channels.containsAnyChannel();

	}

}
