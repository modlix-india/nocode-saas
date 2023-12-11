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
public class LimitAccess extends AbstractUpdatableDTO<ULong, ULong> {

	private static final long serialVersionUID = -486795902844156589L;
	
	private ULong appId;
	private ULong clientId;
	private String name;
	private Long limit;
}
