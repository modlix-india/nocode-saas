package com.fincity.saas.notification.model.channel;

import java.util.List;

import com.fincity.saas.notification.enums.NotificationChannel;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
public class EmailMessage extends AbstractNotificationChannel {

	private String fromAddress;
	private String toAddress;

	private List<String> ccAddresses;
	private List<String> bccAddresses;

	private boolean isHtml;


	@Override
	public NotificationChannel getNotificationChannel() {
		return NotificationChannel.EMAIL;
	}
}
