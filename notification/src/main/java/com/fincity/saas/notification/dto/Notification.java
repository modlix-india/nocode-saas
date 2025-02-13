package com.fincity.saas.notification.dto;

import java.io.Serial;
import java.util.Map;

import org.jooq.types.ULong;

import com.fincity.saas.notification.dto.base.BaseIds;
import com.fincity.saas.notification.enums.NotificationChannelType;
import com.fincity.saas.notification.enums.NotificationType;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;

@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@ToString(callSuper = true)
public class Notification extends BaseIds<Notification> {

	@Serial
	private static final long serialVersionUID = 5488955076177275391L;

	private ULong userId;
	private NotificationType notificationType;
	private Map<NotificationChannelType, Map<String, Object>> channelDetails;
}
