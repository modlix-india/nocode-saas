package com.fincity.security.dto;

import org.jooq.types.ULong;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fincity.saas.commons.model.dto.AbstractUpdatableDTO;
import com.fincity.security.util.RolePermissionUtil;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class Permission extends AbstractUpdatableDTO<ULong, ULong> {

	private static final long serialVersionUID = 9008366234572200589L;
	
	private ULong clientId;
	private String name;
	private String description;
	
	@JsonProperty("authority")
	public String getAuthorityString() {
		return RolePermissionUtil.toAuthorityString(name);
	}
}
