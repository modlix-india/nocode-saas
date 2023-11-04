package com.fincity.security.dto;

import org.jooq.types.ULong;

import com.fincity.saas.commons.model.dto.AbstractUpdatableDTO;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;

@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@ToString(callSuper = true)
public class AppProperty extends AbstractUpdatableDTO<ULong, ULong> {

	private static final long serialVersionUID = -486795902544156589L;

	private ULong clientId;
	private ULong appId;
	private String name;
	private String value;
}
