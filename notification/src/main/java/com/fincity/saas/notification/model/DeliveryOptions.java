package com.fincity.saas.notification.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@NoArgsConstructor
public class DeliveryOptions {

	private boolean instant = Boolean.TRUE;
	private String cronStatement;
	private boolean allowUnsubscribing = Boolean.TRUE;

}
