package com.fincity.saas.notification.dto.base;

import java.io.Serial;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.Accessors;

@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@NoArgsConstructor
@ToString(callSuper = true)
public abstract class AbstractBaseDto<D extends AbstractBaseDto<D>> extends AbstractIdsDto<AbstractBaseDto<D>> {

	@Serial
	private static final long serialVersionUID = 1725787359073153168L;

	private String code;
	private String name;
	private String description;
}
