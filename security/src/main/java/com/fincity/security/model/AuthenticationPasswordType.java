package com.fincity.security.model;

import lombok.Getter;

@Getter
public enum AuthenticationPasswordType {

	PASSWORD("Password"),
	PIN("PIN"),
	OTP("OTP");

	private final String name;

	AuthenticationPasswordType(String name) {
		this.name = name;
	}
}
