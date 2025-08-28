package com.fincity.saas.message.dto.message.provider.whatsapp;

import com.fincity.saas.message.dto.base.BaseUpdatableDto;
import com.fincity.saas.message.enums.message.provider.whatsapp.cloud.MessageStatus;
import com.fincity.saas.message.enums.message.provider.whatsapp.cloud.MessageType;
import com.fincity.saas.message.model.common.PhoneNumber;
import com.fincity.saas.message.model.message.whatsapp.messages.Message;
import com.fincity.saas.message.model.message.whatsapp.messages.response.MessageResponse;
import com.fincity.saas.message.model.message.whatsapp.webhook.IMessage;
import com.fincity.saas.message.util.PhoneUtil;
import java.io.Serial;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;
import lombok.experimental.FieldNameConstants;
import org.jooq.types.ULong;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@FieldNameConstants
public class WhatsappMessage extends BaseUpdatableDto<WhatsappMessage> {

    @Serial
    private static final long serialVersionUID = 85222336061195313L;

    private String whatsappBusinessAccountId;
    private String messageId;

    private ULong whatsappPhoneNumberId;
    private Integer fromDialCode = PhoneUtil.getDefaultCallingCode();
    private String from;
    private Integer toDialCode = PhoneUtil.getDefaultCallingCode();
    private String to;

    private String customerWaId;

    private MessageType messageType;

    private MessageStatus messageStatus;
    private LocalDateTime sentTime;
    private LocalDateTime deliveredTime;
    private LocalDateTime readTime;
    private LocalDateTime failedTime;
    private String failureReason;

    private boolean isOutbound;

    private Message message;
    private IMessage inMessage;
    private MessageResponse messageResponse;

    public WhatsappMessage() {
        super();
    }

    public static WhatsappMessage ofOutbound(Message message, PhoneNumber from) {

        PhoneNumber to = PhoneNumber.of(message.getTo());

        return new WhatsappMessage()
                .setFromDialCode(from.getCountryCode())
                .setFrom(from.getNumber())
                .setToDialCode(to.getCountryCode())
                .setTo(to.getNumber())
                .setMessageType(message.getType())
                .setMessageStatus(MessageStatus.SENT)
                .setSentTime(LocalDateTime.now())
                .setOutbound(Boolean.TRUE)
                .setMessage(message);
    }

    public static WhatsappMessage ofInbound(
            IMessage message, String whatsappBusinessAccountId, ULong whatsappPhoneNumberId) {

        PhoneNumber from = PhoneNumber.of(message.getFrom());

        return new WhatsappMessage()
                .setWhatsappBusinessAccountId(whatsappBusinessAccountId)
                .setMessageId(message.getId())
                .setCustomerWaId(message.getContacts().getFirst().getWaId())
                .setWhatsappPhoneNumberId(whatsappPhoneNumberId)
                .setFromDialCode(from.getCountryCode())
                .setFrom(from.getNumber())
                .setMessageType(message.getType())
                .setMessageStatus(MessageStatus.DELIVERED)
                .setDeliveredTime(message.getTimestampAsDate())
                .setOutbound(Boolean.FALSE)
                .setInMessage(message);
    }
}
