package com.fincity.saas.core.model;

import java.math.BigInteger;
import java.util.Map;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@NoArgsConstructor
public class NotificationRequest {

	private String appCode;
	private String clientCode;
	private BigInteger userId;
	private String notificationName;
	private Map<String, Object> objectMap;
}
