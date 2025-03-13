package com.fincity.saas.notification.model.message;

import java.util.List;
import java.util.Map;

import com.fincity.saas.notification.enums.NotificationChannelType;
import com.fincity.saas.notification.enums.Priority;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class EmailMessage extends NotificationMessage<EmailMessage> {

	private String fromAddress;
	private String toAddress;

	private List<String> ccAddresses;
	private List<String> bccAddresses;

	private boolean isHtml;
	private List<String> replyTo;
	private Map<String, String> headers;
	private Priority priority;

	@Override
	public NotificationChannelType getChannelType() {
		return NotificationChannelType.EMAIL;
	}

	@Override
	public EmailMessage addRecipientInfo(RecipientInfo recipientInfo) {
		this.fromAddress = recipientInfo.getFromAddress();
		this.toAddress = recipientInfo.getToAddress();
		this.ccAddresses = recipientInfo.getCcAddress();
		this.bccAddresses = recipientInfo.getBccAddress();
		this.replyTo = recipientInfo.getReplyTo();
		return this;
	}
}
