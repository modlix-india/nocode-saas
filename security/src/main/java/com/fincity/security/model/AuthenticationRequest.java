package com.fincity.security.model;

import org.jooq.types.ULong;

import lombok.Data;

@Data
public class AuthenticationRequest {

	private String userName;
	private String password;
	private ULong userId;

	private AuthenticationIdentifierType identifierType;
	private boolean rememberMe = false;
	private boolean cookie = false;
}
