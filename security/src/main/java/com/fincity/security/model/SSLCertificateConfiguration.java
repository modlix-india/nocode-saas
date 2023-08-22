package com.fincity.security.model;

import java.io.Serializable;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class SSLCertificateConfiguration implements Serializable {

	private static final long serialVersionUID = 6666609928424508316L;

	private String url;
	private String clientCode;
	private String appCode;
	private String privateKey;
	private String certificate;
}
