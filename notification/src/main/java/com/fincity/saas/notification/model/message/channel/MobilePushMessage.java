package com.fincity.saas.notification.model.message.channel;

import com.fincity.saas.notification.enums.channel.NotificationChannelType;
import com.fincity.saas.notification.model.message.NotificationMessage;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class MobilePushMessage extends NotificationMessage<MobilePushMessage> {

	@Override
	public NotificationChannelType getChannelType() {
		return NotificationChannelType.MOBILE_PUSH;
	}
}
