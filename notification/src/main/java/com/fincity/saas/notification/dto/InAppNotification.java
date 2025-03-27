package com.fincity.saas.notification.dto;

import java.io.Serial;
import java.time.LocalDateTime;
import java.util.Map;

import org.jooq.types.ULong;

import com.fincity.saas.commons.model.dto.AbstractUpdatableDTO;
import com.fincity.saas.notification.enums.NotificationDeliveryStatus;
import com.fincity.saas.notification.enums.NotificationType;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;
import lombok.experimental.FieldNameConstants;

@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@ToString(callSuper = true)
@FieldNameConstants
public class InAppNotification extends AbstractUpdatableDTO<ULong, ULong> {

	@Serial
	private static final long serialVersionUID = 4387019101849838824L;

	private String code;
	private String clientCode;
	private String appCode;
	private ULong userId;
	private NotificationType notificationType;
	private Map<String, Object> notificationMessage;
	private NotificationDeliveryStatus deliveryStatus;
	private LocalDateTime sentTime;
	private LocalDateTime deliveredTime;
	private LocalDateTime readTime;

}
