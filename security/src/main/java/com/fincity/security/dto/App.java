package com.fincity.security.dto;

import org.jooq.types.ULong;

import com.fincity.saas.commons.model.dto.AbstractUpdatableDTO;
import com.fincity.security.jooq.enums.SecurityAppAppType;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)

public class App extends AbstractUpdatableDTO<ULong, ULong> {

	private static final long serialVersionUID = -486795902544156589L;

	private ULong clientId;
	private String appName;
	private String appCode;
	private SecurityAppAppType appType;
}
