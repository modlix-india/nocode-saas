package com.fincity.saas.core.dto;

import java.io.Serial;
import java.time.LocalDateTime;

import org.jooq.types.ULong;

import com.fincity.saas.commons.model.dto.AbstractDTO;
import com.fincity.saas.core.jooq.enums.CoreTokensTokenType;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;

@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@ToString(callSuper = true)
public class CoreToken extends AbstractDTO<ULong, ULong> {

	@Serial
	private static final long serialVersionUID = 6585106241902821249L;

	private ULong userId;
	private String clientCode;
	private String appCode;
	private String connectionName;
	private CoreTokensTokenType tokenType;
	private String token;
	private Boolean isRevoked;
	private LocalDateTime expiresAt;

}
