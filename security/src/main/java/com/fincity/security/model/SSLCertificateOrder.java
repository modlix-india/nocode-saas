package com.fincity.security.model;

import java.io.Serializable;
import java.util.List;

import com.fincity.security.dto.SSLChallenge;
import com.fincity.security.dto.SSLRequest;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class SSLCertificateOrder implements Serializable {

	private static final long serialVersionUID = -6051386366286270924L;

	private SSLRequest request;
	private List<SSLChallenge> challenges;
}
