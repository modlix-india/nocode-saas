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
import com.fincity.saas.notification.enums.NotificationChannelType;
import com.fincity.saas.notification.enums.NotificationDeliveryStatus;
import com.fincity.saas.notification.enums.NotificationStage;
import com.fincity.saas.notification.enums.NotificationType;
import com.fincity.saas.notification.model.response.NotificationErrorInfo;
import com.fincity.saas.notification.model.SendRequest;

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

	private String code;
	private String clientCode;
	private String appCode;
	private ULong userId;
	private NotificationType notificationType;
	private Map<String, Object> notificationMessage;
	private NotificationStage notificationStage;
	private LocalDateTime triggerTime;
	private boolean isEmail = Boolean.FALSE;
	private Map<String, LocalDateTime> emailDeliveryStatus = HashMap
			.newHashMap(NotificationDeliveryStatus.values().length);
	private boolean isInApp = Boolean.FALSE;
	private Map<String, LocalDateTime> inAppDeliveryStatus = HashMap
			.newHashMap(NotificationDeliveryStatus.values().length);
	private boolean isMobilePush = Boolean.FALSE;
	private Map<String, LocalDateTime> mobilePushDeliveryStatus = HashMap
			.newHashMap(NotificationDeliveryStatus.values().length);
	private boolean isWebPush = Boolean.FALSE;
	private Map<String, LocalDateTime> webPushDeliveryStatus = HashMap
			.newHashMap(NotificationDeliveryStatus.values().length);
	private boolean isSms = Boolean.FALSE;
	private Map<String, LocalDateTime> smsDeliveryStatus = HashMap
			.newHashMap(NotificationDeliveryStatus.values().length);
	private boolean isError = Boolean.FALSE;
	private Integer errorCode;
	private String errorMessageId;
	private String errorMessage;

	public static SentNotification from(SendRequest request, LocalDateTime triggerTime) {
		return new SentNotification()
				.setCode(request.getCode() != null ? request.getCode() : UniqueUtil.shortUUID())
				.setClientCode(request.getClientCode())
				.setAppCode(request.getAppCode())
				.setUserId(ULongUtil.valueOf(request.getUserId()))
				.setNotificationType(request.getNotificationType())
				.setNotificationMessage(request.getChannels() != null ? request.getChannels().toMap() : Map.of())
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
			setStatus.accept(HashMap.newHashMap(NotificationDeliveryStatus.values().length));
			return;
		}

		Map<String, LocalDateTime> currentStatus = override
				? getStatus.get()
				: HashMap.newHashMap(NotificationDeliveryStatus.values().length);

		currentStatus.putAll(deliveryStatus);
		setStatus.accept(currentStatus);
	}

	public SentNotification setErrorInfo(NotificationErrorInfo notificationErrorInfo) {

		if (notificationErrorInfo == null)
			return this;

		this.setError(Boolean.TRUE);
		this.setErrorMessageId(notificationErrorInfo.getMessageId());
		this.setErrorCode(notificationErrorInfo.getErrorCode().value());
		this.setErrorMessage(notificationErrorInfo.getErrorMessage());
		return this;
	}

}
