package com.fincity.security.model;

import org.springframework.http.HttpStatus;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.util.StringUtil;

public interface BasePassword<T extends BasePassword<T>> {

	default String getPassword() {
		return null;
	}

	default T setPassword(String password) {
		throw new GenericException(HttpStatus.BAD_REQUEST, "Password is not supported in this implementation.");
	}

	default String getPin() {
		return null;
	}

	default T setPin(String pin) {
		throw new GenericException(HttpStatus.BAD_REQUEST, "PIN is not supported in this implementation.");
	}

	default String getOtp() {
		return null;
	}

	default T setOtp(String otp) {
		throw new GenericException(HttpStatus.BAD_REQUEST, "OTP is not supported for this implementation.");
	}

	@JsonIgnore
	default AuthenticationPasswordType getPassType() {
		return null;
	}

	default T setPassType(AuthenticationPasswordType passType) {
		throw new GenericException(HttpStatus.BAD_REQUEST, "Password Type is not supported in this implementation.");
	}

	@JsonIgnore
	default AuthenticationPasswordType getInputPassType() {

		if (getPassType() != null)
			return getPassType();

		if (!StringUtil.safeIsBlank(getPassword()))
			return AuthenticationPasswordType.PASSWORD;

		if (!StringUtil.safeIsBlank(getPin()))
			return AuthenticationPasswordType.PIN;

		if (!StringUtil.safeIsBlank(getOtp()))
			return AuthenticationPasswordType.OTP;

		return null;
	}

	@JsonIgnore
	default String getInputPass() {

		AuthenticationPasswordType type = getPassType();
		if (type != null) {
			return switch (type) {
				case PASSWORD -> getPassword();
				case PIN -> getPin();
				case OTP -> getOtp();
			};
		}

		if (!StringUtil.safeIsBlank(getPassword()))
			return getPassword();

		if (!StringUtil.safeIsBlank(getPin()))
			return getPin();

		if (!StringUtil.safeIsBlank(getOtp()))
			return getOtp();

		return null;
	}

	@JsonIgnore
	default String getInputPass(AuthenticationPasswordType passType) {
		return switch (passType) {
			case PASSWORD -> getInputPass();
			case PIN -> getPin();
			case OTP -> getOtp();
		};
	}

}
