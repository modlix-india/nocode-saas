package com.fincity.security.model;

import org.jooq.types.ULong;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class AuthenticationRequest {

	private String userName;
	private String password;
	private ULong userId;
	private String otp;

	private AuthenticationIdentifierType identifierType;
	private boolean rememberMe = false;
	private boolean cookie = false;
	private boolean generateOtp = false;
}
