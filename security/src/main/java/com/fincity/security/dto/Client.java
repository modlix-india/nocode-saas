package com.fincity.security.dto;

import org.jooq.types.ULong;

import com.fincity.security.jooq.enums.SecurityClientStatusCode;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class Client extends AbstractUpdatableDTO<ULong, ULong> {

	private static final long serialVersionUID = 4312344235572008119L;

	private String code;
	private String name;
	private String typeCode;
	private int tokenValidityMinutes;
	private String localeCode;
	private SecurityClientStatusCode statusCode;
}
