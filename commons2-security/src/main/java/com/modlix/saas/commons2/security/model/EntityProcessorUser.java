package com.modlix.saas.commons2.security.model;

import java.util.Set;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class EntityProcessorUser {

	private Long id;
	private Long roleId;
	private Long designationId;
	private Long departmentId;
	private Set<Long> profileIds;

}
