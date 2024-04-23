package com.fincity.security.model;

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@NoArgsConstructor
@AllArgsConstructor
public class ClientRegistrationResponse implements Serializable {

	private static final long serialVersionUID = 567874378374L;

	private Boolean created;
	private String redirectURL;
	private AuthenticationResponse authentication;
}