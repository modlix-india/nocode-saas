package com.fincity.saas.commons.security.model;

import java.io.Serial;
import java.io.Serializable;
import java.util.Set;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class EntityProcessorUser implements Serializable {

	@Serial
	private static final long serialVersionUID = 3544890660887876636L;

	private Long id;
	private Long roleId;
	private Long designationId;
	private Long departmentId;
	private Set<Long> profileIds;

}
