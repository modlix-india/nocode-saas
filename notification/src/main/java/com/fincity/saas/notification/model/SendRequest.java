package com.fincity.saas.notification.model;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class SendRequest {

	private Integer userId;
	private String notificationTypeId;
	private NotificationChannel channels;

}
