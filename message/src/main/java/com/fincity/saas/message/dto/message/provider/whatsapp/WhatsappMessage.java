package com.fincity.saas.message.dto.message.provider.whatsapp;

import com.fincity.saas.commons.enums.StringEncoder;
import com.fincity.saas.message.dto.base.BaseUpdatableDto;
import com.fincity.saas.message.enums.message.provider.whatsapp.cloud.MessageStatus;
import com.fincity.saas.message.enums.message.provider.whatsapp.cloud.MessageType;
import com.fincity.saas.message.model.common.PhoneNumber;
import com.fincity.saas.message.model.message.whatsapp.messages.Message;
import com.fincity.saas.message.model.message.whatsapp.messages.response.MessageResponse;
import com.fincity.saas.message.model.message.whatsapp.webhook.IContact;
import com.fincity.saas.message.model.message.whatsapp.webhook.IMessage;
import com.fincity.saas.message.model.message.whatsapp.webhook.IMetadata;
import com.fincity.saas.message.oserver.files.model.FileDetail;
import com.fincity.saas.message.util.PhoneUtil;
import java.io.Serial;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
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

    private Integer customerDialCode = PhoneUtil.getDefaultCallingCode();
    private String customerPhoneNumber;
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
    private FileDetail mediaFileDetail;

    public WhatsappMessage() {
        super();
    }

    public WhatsappMessage(WhatsappMessage whatsappMessage) {
        super(whatsappMessage);
        this.whatsappBusinessAccountId = whatsappMessage.whatsappBusinessAccountId;
        this.messageId = whatsappMessage.messageId;
        this.whatsappPhoneNumberId = whatsappMessage.whatsappPhoneNumberId;
        this.fromDialCode = whatsappMessage.fromDialCode;
        this.from = whatsappMessage.from;
        this.toDialCode = whatsappMessage.toDialCode;
        this.to = whatsappMessage.to;
        this.customerDialCode = whatsappMessage.customerDialCode;
        this.customerPhoneNumber = whatsappMessage.customerPhoneNumber;
        this.customerWaId = whatsappMessage.customerWaId;
        this.messageType = whatsappMessage.messageType;
        this.messageStatus = whatsappMessage.messageStatus;
        this.sentTime = whatsappMessage.sentTime;
        this.deliveredTime = whatsappMessage.deliveredTime;
        this.readTime = whatsappMessage.readTime;
        this.failedTime = whatsappMessage.failedTime;
        this.failureReason = whatsappMessage.failureReason;
        this.isOutbound = whatsappMessage.isOutbound;
        this.message = whatsappMessage.message;
        this.inMessage = whatsappMessage.inMessage;
        this.messageResponse = whatsappMessage.messageResponse;
        this.mediaFileDetail = whatsappMessage.mediaFileDetail;
    }

    public static WhatsappMessage ofOutbound(Message message, PhoneNumber from, FileDetail fileDetail) {

        PhoneNumber to = PhoneNumber.of(message.getTo());

        return new WhatsappMessage()
                .setFromDialCode(from.getCountryCode())
                .setFrom(from.getNumber())
                .setToDialCode(to.getCountryCode())
                .setTo(to.getNumber())
                .setCustomerDialCode(to.getCountryCode())
                .setCustomerPhoneNumber(to.getNumber())
                .setMessageType(message.getType())
                .setMessageStatus(MessageStatus.SENT)
                .setSentTime(LocalDateTime.now())
                .setOutbound(Boolean.TRUE)
                .setMessage(message)
                .setMediaFileDetail(fileDetail);
    }

    public static WhatsappMessage ofInbound(
            IMetadata metadata,
            IContact contact,
            IMessage message,
            String whatsappBusinessAccountId,
            ULong whatsappPhoneNumberId) {

        PhoneNumber from = PhoneNumber.ofWhatsapp(message.getFrom());
        PhoneNumber to = PhoneNumber.ofWhatsapp(metadata.getDisplayPhoneNumber());

        return new WhatsappMessage()
                .setWhatsappBusinessAccountId(whatsappBusinessAccountId)
                .setMessageId(message.getId())
                .setWhatsappPhoneNumberId(whatsappPhoneNumberId)
                .setFromDialCode(from.getCountryCode())
                .setFrom(from.getNumber())
                .setToDialCode(to.getCountryCode())
                .setTo(to.getNumber())
                .setCustomerDialCode(from.getCountryCode())
                .setCustomerPhoneNumber(from.getNumber())
                .setCustomerWaId(contact.getWaId())
                .setMessageType(message.getType())
                .setMessageStatus(MessageStatus.DELIVERED)
                .setDeliveredTime(
                        message.getTimestamp() != null
                                ? LocalDateTime.ofInstant(
                                        Instant.ofEpochSecond(Long.parseLong(message.getTimestamp())), ZoneOffset.UTC)
                                : LocalDateTime.now())
                .setOutbound(Boolean.FALSE)
                .setInMessage(message);
    }

    public WhatsappMessage update(String businessAccountId, ULong whatsappPhoneNumberId, MessageResponse response) {
        this.setWhatsappBusinessAccountId(businessAccountId);
        this.setWhatsappPhoneNumberId(whatsappPhoneNumberId);
        this.setCustomerWaId(response.getContacts().getFirst().getWaId());
        this.setMessageId(response.getMessages().getFirst().getId());
        this.setMessageResponse(response);

        return this;
    }

    public String getBase64CustomerPhoneNumber() {
        return this.getCustomerPhoneNumber() != null
                ? StringEncoder.BASE64.encode(this.getCustomerPhoneNumber().getBytes(), true, false)
                : null;
    }
}
