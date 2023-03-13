package com.fincity.security.model;

import java.math.BigInteger;
import java.time.LocalDateTime;

import com.fincity.saas.common.security.jwt.ContextUser;
import com.fincity.security.dto.Client;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class AuthenticationResponse {

	private ContextUser user;
	private Client client;
	private String loggedInClientCode;
	private BigInteger loggedInClientId;

	private String accessToken;
	private LocalDateTime accessTokenExpiryAt;
}
