package com.fincity.security.dto;

import java.time.LocalDateTime;

import org.jooq.types.ULong;

import com.fincity.saas.commons.model.dto.AbstractUpdatableDTO;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
public class SSLChallenge extends AbstractUpdatableDTO<ULong, ULong> {

	private static final long serialVersionUID = -2863894026388440166L;

	private ULong requestId;
	private String challengeType;
	private String domain;
	private String token;
	private String authorization;
	private String status;
	private String failedReason;
	private LocalDateTime lastValidatedAt;
	private Integer retryCount;
}