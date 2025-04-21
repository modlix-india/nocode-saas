package com.fincity.saas.entity.processor.dto;

import java.io.Serial;

import org.jooq.types.ULong;

import com.fincity.saas.commons.jooq.flow.dto.AbstractFlowUpdatableDTO;
import com.fincity.saas.commons.util.UniqueUtil;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;
import lombok.experimental.FieldNameConstants;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@FieldNameConstants
public class BaseDto<T extends BaseDto<T>> extends AbstractFlowUpdatableDTO<ULong, ULong> {

	@Serial
	private static final long serialVersionUID = 1844345864104376760L;

	private String code = UniqueUtil.shortUUID();

	private String name;
	private String description;
	private ULong addedByUserId;

	private boolean tempActive = Boolean.FALSE;
	private boolean isActive = Boolean.TRUE;

	public T setCode() {

		if (this.code != null) return (T) this;

		this.code = UniqueUtil.shortUUID();
		return (T) this;
	}
}
