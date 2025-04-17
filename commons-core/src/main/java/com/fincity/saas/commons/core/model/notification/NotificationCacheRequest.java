package com.fincity.saas.commons.core.model.notification;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@NoArgsConstructor
public class NotificationCacheRequest implements Serializable {

	@Serial
	private static final long serialVersionUID = 7902977016851977819L;

	private String appCode;
	private String clientCode;
	private String entityName;
	private Map<String, String> channelEntities;
}
