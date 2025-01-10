package com.fincity.security.enums.otp;

import lombok.Getter;

@Getter
public enum OtpPurpose {

	REGISTRATION("registration", (short) 1),
	LOGIN("login", (short) 0),
	VERIFICATION("verification", (short) 0),
	PASSWORD_RESET("password reset", (short) 0);

	private final String displayName;

	private final Short verifyLegsCounts;

	OtpPurpose(String displayName, Short verifyLegsCounts) {
		this.displayName = displayName;
		this.verifyLegsCounts = verifyLegsCounts;
	}

}
