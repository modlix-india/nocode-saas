package com.fincity.security.model;

import lombok.Data;

@Data
public class RequestUpdatePassword {

	private String oldPassword;
	private String newPassword;
	private String confirmPassword;
}
