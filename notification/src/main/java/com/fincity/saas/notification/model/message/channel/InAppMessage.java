package com.fincity.saas.notification.model.message.channel;

import java.io.Serial;
import java.util.Map;

import com.fincity.saas.notification.enums.channel.NotificationChannelType;
import com.fincity.saas.notification.model.message.NotificationMessage;
import com.fincity.saas.notification.model.message.action.Action;
import com.fincity.saas.notification.model.message.action.Action.MultiAction;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class InAppMessage extends NotificationMessage<InAppMessage> implements MultiAction<InAppMessage> {

	@Serial
	private static final long serialVersionUID = 950463081529396480L;

	private String iconUrl;
	private String imageUrl;
	private Map<String, Action> actions;

	@Override
	public NotificationChannelType getChannelType() {
		return NotificationChannelType.IN_APP;
	}
}
