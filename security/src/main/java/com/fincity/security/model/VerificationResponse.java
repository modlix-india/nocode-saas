package com.fincity.security.model;

import java.time.LocalDateTime;

import com.fincity.saas.common.security.jwt.ContextUser;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class VerificationResponse {

	private ContextUser user;

	private String accessToken;
	private LocalDateTime accessTokenExpiryAt;
}
