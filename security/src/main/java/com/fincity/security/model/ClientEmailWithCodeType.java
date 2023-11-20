package com.fincity.security.model;

import java.io.Serializable;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class ClientEmailWithCodeType implements Serializable {

	private static final long serialVersionUID = 6666609928424508398L;

	private String emailId;
	private CodeType code;

}
