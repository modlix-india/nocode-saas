package com.fincity.saas.notification.dto;

import com.fincity.saas.commons.jooq.util.ULongUtil;
import com.fincity.saas.commons.model.dto.AbstractUpdatableDTO;
import com.fincity.saas.notification.enums.NotificationDeliveryStatus;
import com.fincity.saas.notification.enums.NotificationStage;
import com.fincity.saas.notification.enums.NotificationType;
import com.fincity.saas.notification.enums.channel.NotificationChannelType;
import com.fincity.saas.notification.model.message.action.Action;
import com.fincity.saas.notification.model.message.channel.InAppMessage;
import com.fincity.saas.notification.model.request.SendRequest;
import java.io.Serial;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;
import lombok.experimental.FieldNameConstants;
import org.jooq.types.ULong;

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
    private InAppMessage inAppMessage;
    private NotificationStage notificationStage;
    private NotificationDeliveryStatus notificationDeliveryStatus;
    private Map<String, Action> actions;
    private LocalDateTime triggerTime;

    private boolean sent = Boolean.FALSE;
    private LocalDateTime sentTime;
    private boolean delivered = Boolean.FALSE;
    private LocalDateTime deliveredTime;
    private boolean read = Boolean.FALSE;
    private LocalDateTime readTime;
    private boolean failed = Boolean.FALSE;
    private LocalDateTime failedTime;

    public static InAppNotification from(SendRequest request, LocalDateTime triggerTime) {

        if (request == null || !request.getChannels().containsChannel(NotificationChannelType.IN_APP)) return null;

        InAppMessage inAppMessage = request.getChannels().getInApp();

        return new InAppNotification()
                .setCode(request.getCode())
                .setClientCode(request.getClientCode())
                .setAppCode(request.getAppCode())
                .setUserId(ULongUtil.valueOf(request.getUserId()))
                .setNotificationType(request.getNotificationType())
                .setInAppMessage(inAppMessage)
                .setActions(inAppMessage.getActions() != null ? inAppMessage.getActions() : null)
                .setTriggerTime(triggerTime);
    }

    public InAppNotification setNotificationDeliveryStatus(
            NotificationDeliveryStatus status, LocalDateTime createdTime) {

        this.notificationDeliveryStatus = status;

        switch (status) {
            case SENT -> {
                this.sent = Boolean.TRUE;
                this.sentTime = createdTime;
            }
            case DELIVERED -> {
                this.delivered = Boolean.TRUE;
                this.deliveredTime = createdTime;
            }
            case READ -> {
                this.read = Boolean.TRUE;
                this.readTime = createdTime;
            }
            case FAILED -> {
                this.failed = Boolean.TRUE;
                this.failedTime = createdTime;
            }
            default -> {
                // no update
            }
        }
        return this;
    }
}
