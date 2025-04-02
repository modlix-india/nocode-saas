package com.fincity.saas.notification.model.request;

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
	private static final long serialVersionUID = 7766241875367352716L;

	private String appCode;
	private String clientCode;
	private String entityName;
	private Map<String, String> channelEntities;

	public boolean hasChannelEntities() {
		return this.channelEntities != null && !this.channelEntities.isEmpty();
	}

	public boolean isEmpty() {
		return this.appCode == null || this.clientCode == null || this.entityName == null;
	}
}
