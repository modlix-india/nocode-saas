package com.fincity.security.model;

import org.jooq.types.ULong;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fincity.saas.commons.util.StringUtil;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class AuthenticationRequest {

	private String userName;
	private String password;
	private ULong userId;
	private String otp = null;
	private boolean isResend = false;
	private String pin = null;

	private String socialRegisterState;

	private AuthenticationIdentifierType identifierType;
	private boolean rememberMe = false;
	private boolean cookie = false;
	private boolean generateOtp = false;

	@JsonIgnore
	public String getInputPassword() {
		if (!StringUtil.safeIsBlank(password))
			return password;

		if (!StringUtil.safeIsBlank(pin))
			return pin;

		return otp;
	}

	@JsonIgnore
	public AuthenticationPasswordType getPasswordType() {

		if (!StringUtil.safeIsBlank(password))
			return AuthenticationPasswordType.PASSWORD;

		if (!StringUtil.safeIsBlank(pin))
			return AuthenticationPasswordType.PIN;

		return AuthenticationPasswordType.OTP;
	}
}
