package com.fincity.saas.notification.model.message.channel;

import java.io.Serial;

import com.fincity.saas.notification.enums.channel.NotificationChannelType;
import com.fincity.saas.notification.model.message.NotificationMessage;
import com.fincity.saas.notification.model.message.recipient.RecipientInfo;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class SmsMessage extends NotificationMessage<SmsMessage> {

	@Serial
	private static final long serialVersionUID = 5065136200116564131L;

	private String senderName;
	private String senderId;
	private String templateName;
	private String peid;
	private String ctid;

	@Override
	public NotificationChannelType getChannelType() {
		return NotificationChannelType.SMS;
	}

	@Override
	public SmsMessage addRecipientInfo(RecipientInfo recipientInfo) {
		this.senderId = recipientInfo.getSenderId();
		this.senderName = recipientInfo.getSenderName();
		return this;
	}
}
