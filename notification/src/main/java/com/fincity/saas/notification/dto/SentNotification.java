package com.fincity.saas.notification.dto;

import java.time.LocalDateTime;

import org.jooq.types.ULong;

import com.fincity.saas.commons.model.dto.AbstractUpdatableDTO;
import com.fincity.saas.notification.enums.NotificationDeliveryStatus;
import com.fincity.saas.notification.enums.NotificationType;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;

@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@ToString(callSuper = true)
public class SentNotification extends AbstractUpdatableDTO<ULong, ULong> {

	private String code;
	private String clientCode;
	private String appCode;
	private ULong userId;
	private String notificationMessage;
	private NotificationType notificationType;
	private LocalDateTime triggerTime;
	private boolean isEmail = Boolean.FALSE;
	private NotificationDeliveryStatus emailDeliveryStatus;
	private LocalDateTime emailDeliveryTime;
	private boolean isInApp = Boolean.FALSE;
	private NotificationDeliveryStatus inAppDeliveryStatus;
	private LocalDateTime inAppDeliveryTime;
	private boolean isMobilePush = Boolean.FALSE;
	private NotificationDeliveryStatus mobilePushDeliveryStatus;
	private LocalDateTime mobilePushDeliveryTime;
	private boolean isWebPush = Boolean.FALSE;
	private NotificationDeliveryStatus webPushDeliveryStatus;
	private LocalDateTime webPushDeliveryTime;
	private boolean isSms = Boolean.FALSE;
	private NotificationDeliveryStatus smsDeliveryStatus;
	private LocalDateTime smsDeliveryTime;
}
