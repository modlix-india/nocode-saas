package com.fincity.saas.notification.model.message.recipient;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

import com.fincity.saas.commons.jooq.enums.notification.NotificationRecipientType;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class RecipientInfo implements Serializable {

	@Serial
	private static final long serialVersionUID = 2894522683777970300L;

	private String senderId;
	private String senderName;
	private String phoneNumber;
	private String fromAddress;
	private String toAddress;
	private List<String> bccAddress;
	private List<String> ccAddress;
	private List<String> replyTo;

	public void addRecipientInto(NotificationRecipientType recipientType, String... values) {
		switch (recipientType) {
			case FROM -> this.fromAddress = values[0];
			case TO -> this.toAddress = values[0];
			case CC -> this.ccAddress = List.of(values);
			case BCC -> this.bccAddress = List.of(values);
			case REPLY_TO -> this.replyTo = List.of(values);
			case PHONE_NUMBER -> this.phoneNumber = values[0];
		}
	}

}
