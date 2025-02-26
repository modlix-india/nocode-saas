package com.fincity.saas.notification.model;

import java.math.BigInteger;

import com.fincity.saas.notification.enums.NotificationType;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class SendRequest {

	private String code;
	private BigInteger clientId;
	private BigInteger appId;
	private BigInteger userId;
	private NotificationType notificationType;
	private NotificationChannel channels;

}
