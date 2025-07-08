package com.fincity.security.dto;

import java.io.Serial;

import org.jooq.types.ULong;

import com.fincity.saas.commons.model.dto.AbstractUpdatableDTO;
import com.fincity.security.jooq.enums.SecurityClientStatusCode;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class Client extends AbstractUpdatableDTO<ULong, ULong> {

	@Serial
	private static final long serialVersionUID = 4312344235572008119L;

	private String code;
	private String name;
	private String typeCode;
	private int tokenValidityMinutes;
	private String localeCode;
	private SecurityClientStatusCode statusCode;
	private String businessType;
	private String businessSize;
	private String industry;
}
