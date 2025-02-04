package com.fincity.security.model;

import java.io.Serial;
import java.io.Serializable;

import org.jooq.types.ULong;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class ClientRegistrationRequest implements BasePassword<ClientRegistrationRequest>, Serializable {

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
	private AuthenticationPasswordType passType;
	private String password;
	private String pin = null;
	private String otp = null;
	private boolean businessClient;
	private String businessType;
	private String subDomain;
	private String subDomainSuffix;
	private String socialRegisterState;

}
