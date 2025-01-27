package com.fincity.saas.notification.model;

import com.fincity.saas.notification.model.message.EmailMessage;
import com.fincity.saas.notification.model.message.InAppMessage;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class NotificationChannel {

	private EmailMessage email;
	private InAppMessage inApp;

	public NotificationChannel(EmailMessage email, InAppMessage inApp) {
		this.email = email;
		this.inApp = inApp;
	}

	public NotificationChannel() {
	}

}
