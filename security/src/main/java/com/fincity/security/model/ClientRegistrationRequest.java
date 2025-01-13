package com.fincity.security.model;

import java.io.Serial;
import java.io.Serializable;

import org.jooq.types.ULong;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fincity.saas.commons.util.StringUtil;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class ClientRegistrationRequest implements Serializable {

	@Serial
	private static final long serialVersionUID = 2510675233197533873L;

	private String clientName;
	private String localeCode;
	private ULong userId;
	private String userName;
	private String emailId;
	private String phoneNumber;
	private String firstName;
	private String lastName;
	private String middleName;
	private AuthenticationPasswordType passwordType;
	private String password;
	private String pin;
	private boolean businessClient;
	private String businessType;
	private String uniqueCode;
	private String subDomain;
	private String subDomainSuffix;
	private String socialRegisterState;

	/**
	 * Returns the {@link AuthenticationPasswordType} based on the object's state.
	 * <p>
	 * Checks {@code passwordType}, then {@code password}, and finally {@code pin}.
	 * Returns null if none are applicable.
	 * </p>
	 * 
	 * @return the determined {@link AuthenticationPasswordType}, or null.
	 */
	@JsonIgnore
	public AuthenticationPasswordType getPasswordType() {

		if (this.passwordType != null)
			return this.passwordType;

		if (!StringUtil.safeIsBlank(this.password))
			return AuthenticationPasswordType.PASSWORD;

		if (!StringUtil.safeIsBlank(this.pin))
			return AuthenticationPasswordType.PIN;

		return null;
	}

}
