package com.fincity.security.dto;

import org.jooq.types.ULong;

import com.fincity.saas.commons.model.dto.AbstractDTO;
import com.fincity.security.jooq.enums.SecuritySoxLogActionName;
import com.fincity.security.jooq.enums.SecuritySoxLogObjectName;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class SoxLog extends AbstractDTO<ULong, ULong> {

	private static final long serialVersionUID = 8126327806885448933L;

	private ULong objectId;
	private SecuritySoxLogObjectName objectName;
	private SecuritySoxLogActionName actionName;
	private String description;
}
