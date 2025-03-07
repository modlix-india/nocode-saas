package com.fincity.saas.notification.model;

import java.math.BigInteger;
import java.util.Map;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class NotificationRequest {

	private String appCode;
	private String clientCode;
	private BigInteger userId;
	private String notificationName;
	private Map<String, Object> objectMap;
}
