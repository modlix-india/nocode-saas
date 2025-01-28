package com.fincity.saas.notification.dto;

import org.jooq.types.ULong;

import com.fasterxml.jackson.databind.JsonNode;
import com.fincity.saas.commons.model.dto.AbstractUpdatableDTO;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;

@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@ToString(callSuper = true)
public class Connection extends AbstractUpdatableDTO<ULong, ULong> {

	private ULong clientId;
	private ULong appId;

	private JsonNode connectionDetails;
}
