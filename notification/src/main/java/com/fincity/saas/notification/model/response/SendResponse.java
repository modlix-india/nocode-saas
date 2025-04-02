package com.fincity.saas.notification.model.response;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigInteger;

import com.fincity.saas.notification.enums.NotificationType;
import com.fincity.saas.notification.enums.channel.NotificationChannelType;
import com.fincity.saas.notification.model.request.SendRequest;
import com.fincity.saas.notification.model.message.NotificationMessage;
import com.fincity.saas.notification.model.message.channel.InAppMessage;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@NoArgsConstructor
public class SendResponse<T extends NotificationMessage<T>> implements Serializable {

	@Serial
	private static final long serialVersionUID = 8622089359039376690L;

	private String code;
	private String appCode;
	private String clientCode;
	private BigInteger userId;
	private String notificationType;
	private T notificationMessage;

	private static <T extends NotificationMessage<T>> SendResponse<T> of(String code, String appCode, String clientCode,
			BigInteger userId, NotificationType notificationType, T notificationMessage) {
		return new SendResponse<T>().setCode(code).setAppCode(appCode).setClientCode(clientCode).setUserId(userId)
				.setNotificationType(
						notificationType != null ? notificationType.getLiteral() : NotificationType.INFO.getLiteral())
				.setNotificationMessage(notificationMessage);
	}

	@SuppressWarnings("unchecked")
	public static <T extends NotificationMessage<T>> SendResponse<T> of(SendRequest request,
			NotificationChannelType channelType) {

		if (!request.getChannels().containsChannel(channelType))
			return null;

		return of(request.getCode(), request.getAppCode(), request.getClientCode(), request.getUserId(),
				request.getNotificationType(), (T) request.getChannels().get(channelType));
	}

	public static SendResponse<InAppMessage> ofInApp(SendRequest request) {
		return of(request, NotificationChannelType.IN_APP);
	}
}
