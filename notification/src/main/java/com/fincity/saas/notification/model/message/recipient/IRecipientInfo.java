package com.fincity.saas.notification.model.message.recipient;

import com.fincity.saas.notification.model.message.NotificationMessage;

public interface IRecipientInfo<T extends NotificationMessage<T>> {

	default T addRecipientInfo(RecipientInfo recipientInfo) {
		return (T) this;
	}
}
