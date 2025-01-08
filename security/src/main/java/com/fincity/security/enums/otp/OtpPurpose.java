package com.fincity.security.enums.otp;

import lombok.Getter;

@Getter
public enum OtpPurpose {

	REGISTRATION("registration"),
	LOGIN("login"),
	VERIFICATION("verification"),
	PASSWORD_RESET("password reset");

	private final String displayName;

	OtpPurpose(String displayName) {
		this.displayName = displayName;
	}

}
