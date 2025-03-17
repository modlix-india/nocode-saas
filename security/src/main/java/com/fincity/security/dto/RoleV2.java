package com.fincity.security.dto;

import org.jooq.types.ULong;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fincity.saas.commons.model.dto.AbstractUpdatableDTO;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;

@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@ToString(callSuper = true)
public class RoleV2 extends AbstractUpdatableDTO<ULong, ULong> {

	private static final long serialVersionUID = -7176719786090846975L;

	private ULong clientId;
	private ULong appId;
	// This is more for reading a role to pickup app Name to pickup authorities.
	private String appName;
	private String name;
	private String shortName;
	private String description;
	private String authority;

	private RoleV2[] subRoles;

	@JsonIgnore
	private Permission[] permissions;
}
