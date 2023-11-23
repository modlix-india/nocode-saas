package com.fincity.security.dto;

import org.jooq.types.ULong;

import com.fincity.saas.commons.model.dto.AbstractDTO;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class CodeAccess extends AbstractDTO<ULong, ULong> {

	private String emailId;
	private String code;
	private ULong appId;
	private ULong clientId;
}
