package com.fincity.saas.notification.model.message.channel;

import com.fincity.saas.notification.enums.channel.NotificationChannelType;
import com.fincity.saas.notification.model.message.NotificationMessage;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class WebPushMessage extends NotificationMessage<WebPushMessage> {

	@Override
	public NotificationChannelType getChannelType() {
		return NotificationChannelType.WEB_PUSH;
	}
}
