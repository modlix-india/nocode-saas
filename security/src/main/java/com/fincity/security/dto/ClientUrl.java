package com.fincity.security.dto;

import org.jooq.types.ULong;

import com.fincity.saas.commons.model.dto.AbstractUpdatableDTO;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
public class ClientUrl extends AbstractUpdatableDTO<ULong, ULong> {

	private static final long serialVersionUID = 2962225494941959699L;

	private ULong clientId;
	private String urlPattern;
	
}
