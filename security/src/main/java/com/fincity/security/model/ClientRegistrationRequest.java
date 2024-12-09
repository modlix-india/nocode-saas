package com.fincity.security.model;

import java.io.Serializable;
import lombok.Data;
import lombok.experimental.Accessors;
import org.jooq.types.ULong;

@Data
@Accessors(chain = true)
public class ClientRegistrationRequest implements Serializable {

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
	private boolean businessClient;
	private String businessType;
	private String code;
	private String subDomain;
	private String socialRegisterState;

}
	