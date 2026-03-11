package com.fincity.security.model;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class SSLCertificateRenewalResult implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	private int renewedCount;
	private int failedCount;
	private List<String> errors = new ArrayList<>();
}
