package com.fincity.saas.core.dto;

import java.io.Serial;
import java.time.LocalDateTime;

import com.fasterxml.jackson.databind.JsonNode;
import org.jooq.types.ULong;

import com.fincity.saas.commons.model.dto.AbstractUpdatableDTO;
import com.fincity.saas.core.jooq.enums.CoreTokensTokenType;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;

@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@ToString(callSuper = true)
public class CoreToken extends AbstractUpdatableDTO<ULong, ULong> {

	@Serial
	private static final long serialVersionUID = 6585106241902821249L;

	private ULong userId;
	private String clientCode;
	private String appCode;
	private String connectionName;
	private CoreTokensTokenType tokenType;
	private String token;
	private String state;
	private JsonNode tokenMetadata;
	private Boolean isRevoked;
	private Boolean isLifetimeToken;
	private LocalDateTime expiresAt;

}
