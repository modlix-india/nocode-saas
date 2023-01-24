package com.fincity.security.model;

import lombok.Data;

@Data
public class AuthenticationRequest {

	private String userName;
	private String password;

	private AuthenticationIdentifierType identifierType;
	private boolean rememberMe = false;
	private boolean cookie = false;
}
