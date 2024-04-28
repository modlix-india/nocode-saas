package com.fincity.saas.commons.security.dto;

import java.io.Serializable;
import java.math.BigInteger;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class App implements Serializable {

	private BigInteger id;
	private BigInteger clientId;
	private String appName;
	private String appCode;
	private String appType;
	private String appAccessType;
	private boolean isTemplate;

	private String clientCode; // This the client code of the client who owns the app usually the managing
								// client.

	// These fields will be populated only in case of explicit app details request.
	private BigInteger explicitClientId;
	private String explicitOwnerClientCode; // This is the client code of the client who will be the owner of the app.
}
