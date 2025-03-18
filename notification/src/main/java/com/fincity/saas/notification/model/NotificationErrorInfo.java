package com.fincity.saas.notification.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@NoArgsConstructor
public class NotificationErrorInfo {

	private Integer errorCode;
	private String messageId;
	private String errorMessage;
	private String transId;
}
