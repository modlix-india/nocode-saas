package com.fincity.saas.notification.model.message.channel;

import java.util.List;

import com.fincity.saas.notification.dto.InAppNotification;
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
public class InAppMessage extends NotificationMessage<InAppMessage> implements Action.UniAction<InAppMessage>, MultiAction<InAppMessage> {

	private String iconUrl;
	private List<Action> actions;

	@Override
	public NotificationChannelType getChannelType() {
		return NotificationChannelType.IN_APP;
	}
}
