package com.fincity.security.model;

import org.jooq.types.ULong;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fincity.saas.commons.util.StringUtil;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class AuthenticationRequest implements BasePassword<AuthenticationRequest> {

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
	public AuthenticationRequest setIdentifierType() {
		if (this.identifierType == null)
			this.identifierType = StringUtil.safeIsBlank(this.getUserName()) || this.getUserName()
					.indexOf('@') == -1 ? AuthenticationIdentifierType.USER_NAME
							: AuthenticationIdentifierType.EMAIL_ID;

		return this;
	}
}
