package com.fincity.saas.notification.model.response;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigInteger;
import java.util.Map;

import com.fincity.saas.notification.enums.NotificationType;
import com.fincity.saas.notification.enums.channel.NotificationChannelType;
import com.fincity.saas.notification.model.SendRequest;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@NoArgsConstructor
public class SendResponse implements Serializable {

	@Serial
	private static final long serialVersionUID = 8622089359039376690L;

	private String code;
	private String appCode;
	private String clientCode;
	private BigInteger userId;
	private String notificationType;
	private Map<String, Object> notificationMessage;

	private static SendResponse of(String code, String appCode, String clientCode, BigInteger userId,
			NotificationType notificationType, Map<String, Object> notificationMessage) {
		return new SendResponse().setCode(code).setAppCode(appCode).setClientCode(clientCode).setUserId(userId)
				.setNotificationType(
						notificationType != null ? notificationType.getLiteral() : NotificationType.INFO.getLiteral())
				.setNotificationMessage(notificationMessage);
	}

	public static SendResponse of(SendRequest request, NotificationChannelType channelType) {

		if (!request.getChannels().containsChannel(channelType))
			return null;

		return of(request.getCode(), request.getAppCode(), request.getClientCode(), request.getUserId(),
				request.getNotificationType(), request.getChannels().get(channelType).toMap());
	}

	public static SendResponse ofInApp(SendRequest request) {
		return of(request, NotificationChannelType.IN_APP);
	}
}
