package com.fincity.security.dto;

import org.jooq.types.ULong;

import com.fincity.saas.commons.model.dto.AbstractUpdatableDTO;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class ClientType extends AbstractUpdatableDTO<ULong, ULong> {

	private static final long serialVersionUID = 7511753074939806640L;
	
	private ULong clientId;
	private String urlPattern;
}
