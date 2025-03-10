package com.fincity.saas.notification.model;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigInteger;
import java.util.Map;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@NoArgsConstructor
public class NotificationRequest implements Serializable {

	@Serial
	private static final long serialVersionUID = 7917666317677344663L;

	private String appCode;
	private String clientCode;
	private BigInteger userId;
	private String notificationName;
	private Map<String, Object> objectMap;
}
