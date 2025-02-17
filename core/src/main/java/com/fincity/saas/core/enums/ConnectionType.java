package com.fincity.saas.core.enums;

import java.util.Set;

public enum ConnectionType {

	APP_DATA(ConnectionSubType.MONGO),

	WHATSAPP,

	WHATSAPPTEMPLATE,

	MAIL(ConnectionSubType.SENDGRID, ConnectionSubType.SMTP),

	TEXTMESSAGE,

	REST_API(ConnectionSubType.REST_API_BASIC, ConnectionSubType.REST_API_AUTH, ConnectionSubType.REST_API_OAUTH2),

	NOTIFICATION(ConnectionSubType.NOTIFICATION_DISABLED, ConnectionSubType.NOTIFICATION_EMAIL,
			ConnectionSubType.NOTIFICATION_IN_APP, ConnectionSubType.NOTIFICATION_MOBILE_PUSH,
			ConnectionSubType.NOTIFICATION_WEB_PUSH, ConnectionSubType.NOTIFICATION_SMS);

	private final Set<ConnectionSubType> allowedSubtypes;

	ConnectionType(ConnectionSubType... allowedSubtypes) {

		this.allowedSubtypes = allowedSubtypes == null ? Set.of() : Set.of(allowedSubtypes);
	}

	public boolean hasConnectionSubType(ConnectionSubType subType) {

		return this.allowedSubtypes.contains(subType);
	}
}
