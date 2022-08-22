package com.fincity.security.dto;

import org.jooq.types.ULong;

import com.fincity.saas.commons.model.dto.AbstractUpdatableDTO;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class Permission extends AbstractUpdatableDTO<ULong, ULong> {

	private static final long serialVersionUID = 9008366234572200589L;
	
	private ULong clientId;
	private String name;
	private String description;
}
