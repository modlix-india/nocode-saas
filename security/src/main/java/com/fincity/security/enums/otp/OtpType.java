package com.fincity.security.enums.otp;

import com.fincity.saas.commons.util.CodeUtil;

import lombok.Getter;

@Getter
public enum OtpType {

	NUMERIC_4(4),
	NUMERIC_6(6),
	ALPHA_NUMERIC_4(4),
	ALPHA_NUMERIC_6(6);

	private final int length;

	OtpType(int length) {
		this.length = length;
	}

	public static OtpType fromName(String name, OtpType defaultType) {
		try {
			return OtpType.valueOf(name);
		} catch (IllegalArgumentException illegalArgumentException) {
			return defaultType;
		}
	}

	public String generateOtp() {

		CodeUtil.CodeGenerationConfiguration config = new CodeUtil.CodeGenerationConfiguration()
				.setLength(length)
				.setNumeric(true)
				.setLowercase(false)
				.setUppercase(this == ALPHA_NUMERIC_4 || this == ALPHA_NUMERIC_6)
				.setSpecialChars(false);

		return CodeUtil.generate(config);
	}

}
