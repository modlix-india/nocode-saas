package com.fincity.saas.notification.model.message.channel;

import java.io.Serializable;
import java.util.Map;

import com.fincity.saas.notification.enums.channel.NotificationChannelType;
import com.fincity.saas.notification.model.message.NotificationMessage;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class InAppMessage extends NotificationMessage<InAppMessage> {

	private String iconUrl;
	private Action[] actions;

	@Override
	public NotificationChannelType getChannelType() {
		return NotificationChannelType.IN_APP;
	}

	@Data
	@Accessors(chain = true)
	@NoArgsConstructor
	public static class Action implements Serializable {

		private String actionType;
		private String actionUrl;
		private Map<String, Object> actionParams;
	}
}
