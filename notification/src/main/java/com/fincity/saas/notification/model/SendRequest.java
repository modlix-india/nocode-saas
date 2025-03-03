package com.fincity.saas.notification.model;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigInteger;

import com.fincity.saas.commons.util.UniqueUtil;
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

	private String code;
	private String clientCode;
	private String appCode;
	private BigInteger userId;
	private NotificationType notificationType;
	private NotificationChannel channels;

	public static SendRequest of(String clientCode, String appCode, BigInteger userId, String notificationType, NotificationChannel channels) {
		return new SendRequest()
				.setCode(UniqueUtil.shortUUID())
				.setClientCode(clientCode)
				.setAppCode(appCode)
				.setUserId(userId)
				.setNotificationType(NotificationType.lookupLiteral(notificationType))
				.setChannels(channels);
	}

	public static SendRequest of(String clientCode, String appCode, BigInteger userId, String notificationType) {
		return of(clientCode, appCode, userId, notificationType,
				new NotificationChannelBuilder().isEnabled(Boolean.FALSE).build());
	}

}
