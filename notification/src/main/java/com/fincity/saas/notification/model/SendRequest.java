package com.fincity.saas.notification.model;

import com.fincity.saas.notification.enums.NotificationChannelType;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class SendRequest {

	private Integer userId;
	private String notificationTypeId;
	private NotificationChannelType channels;

}
