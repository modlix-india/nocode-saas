package com.fincity.saas.core.enums;

import java.util.Set;

public enum ConnectionType {

	APP_DATA(ConnectionSubType.MONGO),
	WHATSAPP,
	WHATSAPPTEMPLATE,
	MAIL(ConnectionSubType.OFFICE365, ConnectionSubType.SENDGRID),
	TEXTMESSAGE,
	REST_API(ConnectionSubType.REST_API_BASIC, ConnectionSubType.REST_API_OAUTH2),
	;

	private Set<ConnectionSubType> allowedSubtypes;

	private ConnectionType(ConnectionSubType... allowedSubtypes) {

		this.allowedSubtypes = allowedSubtypes == null ? Set.of() : Set.of(allowedSubtypes);
	}

	public boolean hasConnectionSubType(ConnectionSubType subType) {

		return this.allowedSubtypes.contains(subType);
	}
}
