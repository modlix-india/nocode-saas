package com.fincity.saas.commons.security.dto;

import java.math.BigInteger;

import lombok.Data;

@Data
public class App {

	private BigInteger id;
	private BigInteger clientId;
	private String appName;
	private String appCode;
	private String appType;
	private boolean isTemplate;
}
