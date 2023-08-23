package com.fincity.security.model;

import java.util.List;

import org.jooq.types.ULong;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class SSLCertificateOrderRequest {

	private List<String> domainNames;
	private String organizationName;
	private ULong urlId;
	private Integer validityInMonths = 12;
}
