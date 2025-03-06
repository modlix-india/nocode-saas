package com.fincity.saas.notification.model.message;

public interface IRecipientInfo<T extends NotificationMessage<T>> {

	default T addRecipientInfo(RecipientInfo recipientInfo) {
		return (T) this;
	}
}
