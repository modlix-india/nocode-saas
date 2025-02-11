package com.fincity.saas.notification.model.message;

import com.fincity.saas.notification.enums.NotificationChannelType;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class InAppMessage extends NotificationMessage {

	private String image;

	@Override
	public NotificationChannelType getChannelType() {
		return NotificationChannelType.IN_APP;
	}
}
