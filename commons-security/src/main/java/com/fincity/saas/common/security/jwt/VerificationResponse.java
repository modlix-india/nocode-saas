package com.fincity.saas.common.security.jwt;

import java.time.LocalDateTime;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class VerificationResponse {

	private ContextUser user;

	private String accessToken;
	private LocalDateTime accessTokenExpiryAt;
}
