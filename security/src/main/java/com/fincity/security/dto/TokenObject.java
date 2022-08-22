package com.fincity.security.dto;

import java.time.LocalDateTime;

import org.jooq.types.ULong;

import com.fincity.saas.commons.model.dto.AbstractDTO;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
public class TokenObject extends AbstractDTO<ULong, ULong> {

	private static final long serialVersionUID = -2389674283265151579L;

	private ULong userId;
	private String token;
	private String partToken;
	private LocalDateTime expiresAt;
	private String ipAddress;
}
