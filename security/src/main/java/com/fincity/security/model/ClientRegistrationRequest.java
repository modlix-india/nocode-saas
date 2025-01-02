package com.fincity.security.model;

import java.io.Serial;
import java.io.Serializable;
import lombok.Data;
import lombok.experimental.Accessors;
import org.jooq.types.ULong;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fincity.saas.commons.util.StringUtil;

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
	private String password;
	private String pin;
	private boolean businessClient;
	private String businessType;
	private String uniqueCode;
	private String subDomain;
	private String socialRegisterState;

	@JsonIgnore
	public AuthenticationPasswordType getPasswordType() {

		if (!StringUtil.safeIsBlank(password))
			return AuthenticationPasswordType.PASSWORD;

		if (!StringUtil.safeIsBlank(pin))
			return AuthenticationPasswordType.PIN;

		return null;
	}


}
