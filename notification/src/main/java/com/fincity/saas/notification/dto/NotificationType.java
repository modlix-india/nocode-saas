package com.fincity.saas.notification.dto;

import org.jooq.types.ULong;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fincity.saas.commons.model.dto.AbstractUpdatableDTO;

public class NotificationType extends AbstractUpdatableDTO<ULong, ULong> {

	@JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
	private ULong clientId;

	@JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
	private ULong appId;
	private String code;
	private String name;
}
