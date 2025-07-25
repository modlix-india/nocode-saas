package com.fincity.saas.message.dto.message.provider.whatsapp;

import com.fincity.saas.message.dto.base.BaseUpdatableDto;
import com.fincity.saas.message.model.message.whatsapp.messages.Message;
import com.fincity.saas.message.model.message.whatsapp.messages.type.MessageType;
import com.fincity.saas.message.model.message.whatsapp.webhook.type.MessageStatus;
import com.fincity.saas.message.util.PhoneUtil;
import java.io.Serial;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;
import lombok.experimental.FieldNameConstants;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@FieldNameConstants
public class WhatsappMessage extends BaseUpdatableDto<WhatsappMessage> {

    @Serial
    private static final long serialVersionUID = 85222336061195313L;

    private String messageId;

    private Integer fromDialCode = PhoneUtil.getDefaultCallingCode();
    private String from;
    private Integer toDialCode = PhoneUtil.getDefaultCallingCode();
    private String to;

    private MessageType messageType;

    private MessageStatus status;
    private LocalDateTime sentTime;
    private LocalDateTime deliveredTime;
    private LocalDateTime readTime;
    private LocalDateTime failedTime;
    private String failureReason;

    private boolean isOutbound;

    private Message message;

    public WhatsappMessage() {
        super();
    }

    public WhatsappMessage(String to, MessageType messageType) {
        super();
        this.to = to;
        this.messageType = messageType;
        this.isOutbound = true;
        this.status = MessageStatus.SENT;
        this.sentTime = LocalDateTime.now();
    }

    public WhatsappMessage(String from, String messageId, MessageType messageType) {
        super();
        this.from = from;
        this.messageId = messageId;
        this.messageType = messageType;
        this.isOutbound = false;
        this.status = MessageStatus.DELIVERED;
        this.deliveredTime = LocalDateTime.now();
    }

    public WhatsappMessage updateStatus(MessageStatus status) {
        this.status = status;

        LocalDateTime now = LocalDateTime.now();

        switch (status) {
            case SENT -> {
                if (this.sentTime == null) this.sentTime = now;
            }
            case DELIVERED -> {
                if (this.deliveredTime == null) this.deliveredTime = now;
            }
            case READ -> {
                if (this.readTime == null) this.readTime = now;
            }
            case FAILED -> {
                if (this.failedTime == null) this.failedTime = now;
            }
            default -> {
                // ignored
            }
        }

        return this;
    }

    public WhatsappMessage updateStatus(MessageStatus status, String failureReason) {
        this.updateStatus(status);
        this.failureReason = failureReason;
        return this;
    }
}
