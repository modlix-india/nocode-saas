package com.fincity.security.model;

import lombok.Data;

@Data
public class RequestUpdatePassword {

	private AuthenticationRequest authRequest;
	private String oldPassword;
	private String newPassword;
	private AuthenticationPasswordType passType = AuthenticationPasswordType.PASSWORD;

}
