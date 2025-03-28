package com.fincity.saas.notification.dto;

import java.io.Serial;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.jooq.types.ULong;

import com.fincity.saas.commons.jooq.util.ULongUtil;
import com.fincity.saas.commons.model.dto.AbstractUpdatableDTO;
import com.fincity.saas.commons.util.UniqueUtil;
import com.fincity.saas.notification.enums.NotificationDeliveryStatus;
import com.fincity.saas.notification.enums.NotificationStage;
import com.fincity.saas.notification.enums.NotificationType;
import com.fincity.saas.notification.enums.channel.NotificationChannelType;
import com.fincity.saas.notification.model.NotificationChannel;
import com.fincity.saas.notification.model.SendRequest;
import com.fincity.saas.notification.model.response.NotificationErrorInfo;

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
public class SentNotification extends AbstractUpdatableDTO<ULong, ULong> {

	@Serial
	private static final long serialVersionUID = 8064924743393465814L;

	private static final int DELIVERY_STATUS_SIZE = NotificationDeliveryStatus.values().length;
	private static final int CHANNEL_SIZE = NotificationChannelType.values().length;

	private String code;
	private String clientCode;
	private String appCode;
	private ULong userId;
	private NotificationType notificationType;
	private NotificationChannel notificationChannel;
	private NotificationStage notificationStage;
	private LocalDateTime triggerTime;
	private boolean isEmail = Boolean.FALSE;
	private Map<String, LocalDateTime> emailDeliveryStatus = HashMap.newHashMap(DELIVERY_STATUS_SIZE);
	private boolean isInApp = Boolean.FALSE;
	private Map<String, LocalDateTime> inAppDeliveryStatus = HashMap.newHashMap(DELIVERY_STATUS_SIZE);
	private boolean isMobilePush = Boolean.FALSE;
	private Map<String, LocalDateTime> mobilePushDeliveryStatus = HashMap.newHashMap(DELIVERY_STATUS_SIZE);
	private boolean isWebPush = Boolean.FALSE;
	private Map<String, LocalDateTime> webPushDeliveryStatus = HashMap.newHashMap(DELIVERY_STATUS_SIZE);
	private boolean isSms = Boolean.FALSE;
	private Map<String, LocalDateTime> smsDeliveryStatus = HashMap.newHashMap(DELIVERY_STATUS_SIZE);
	private boolean isError = Boolean.FALSE;
	private NotificationErrorInfo errorInfo;
	private Map<String, Object> channelErrors = HashMap.newHashMap(CHANNEL_SIZE);

	public static SentNotification from(SendRequest request, LocalDateTime triggerTime) {
		return new SentNotification()
				.setCode(request.getCode() != null ? request.getCode() : UniqueUtil.shortUUID())
				.setClientCode(request.getClientCode())
				.setAppCode(request.getAppCode())
				.setUserId(ULongUtil.valueOf(request.getUserId()))
				.setNotificationType(request.getNotificationType())
				.setNotificationChannel(request.getChannels())
				.setTriggerTime(triggerTime)
				.setErrorInfo(request.getErrorInfo());
	}

	public void updateChannelInfo(NotificationChannelType channelType, Boolean isEnabled,
	                              Map<String, LocalDateTime> deliveryStatus, boolean override) {

		switch (channelType) {
			case EMAIL -> this.updateChannelInfo(isEnabled, deliveryStatus, this::setEmail,
					this::getEmailDeliveryStatus, this::setEmailDeliveryStatus, override);
			case IN_APP -> this.updateChannelInfo(isEnabled, deliveryStatus, this::setInApp,
					this::getInAppDeliveryStatus, this::setInAppDeliveryStatus, override);
			case MOBILE_PUSH -> this.updateChannelInfo(isEnabled, deliveryStatus, this::setMobilePush,
					this::getMobilePushDeliveryStatus, this::setMobilePushDeliveryStatus, override);
			case WEB_PUSH -> this.updateChannelInfo(isEnabled, deliveryStatus, this::setWebPush,
					this::getWebPushDeliveryStatus, this::setWebPushDeliveryStatus, override);
			case SMS -> this.updateChannelInfo(isEnabled, deliveryStatus, this::setSms,
					this::getSmsDeliveryStatus, this::setSmsDeliveryStatus, override);
			default -> {
				// do nothing
			}
		}
	}

	private void updateChannelInfo(Boolean isEnabled, Map<String, LocalDateTime> deliveryStatus,
	                               Consumer<Boolean> setEnabled, Supplier<Map<String, LocalDateTime>> getStatus,
	                               Consumer<Map<String, LocalDateTime>> setStatus, boolean override) {

		setEnabled.accept(isEnabled);

		if (Boolean.FALSE.equals(isEnabled)) {
			setStatus.accept(HashMap.newHashMap(DELIVERY_STATUS_SIZE));
			return;
		}

		Map<String, LocalDateTime> currentStatus = override ? getStatus.get() : HashMap.newHashMap(DELIVERY_STATUS_SIZE);

		currentStatus.putAll(deliveryStatus);
		setStatus.accept(currentStatus);
	}

	public SentNotification setErrorInfo(NotificationErrorInfo notificationErrorInfo) {

		if (notificationErrorInfo == null)
			return this;

		this.errorInfo = notificationErrorInfo;
		this.isError = Boolean.TRUE;

		return this;
	}

	public SentNotification setChannelErrorInfo(Map<String, NotificationErrorInfo> channelErrors) {

		if (channelErrors == null || channelErrors.isEmpty())
			return this;

		if (this.channelErrors == null)
			this.channelErrors = HashMap.newHashMap(CHANNEL_SIZE);

		channelErrors.forEach((key, value) -> this.channelErrors.put(key, value.toMap()));

		this.isError = Boolean.TRUE;

		return this;
	}

}
