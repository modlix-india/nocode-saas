package com.fincity.saas.notification.dto.base;

import com.fincity.saas.commons.util.UniqueUtil;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;

@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@ToString(callSuper = true)
public abstract class BaseInfo<I extends BaseInfo<I>> extends BaseIds<I> {

	private String code;
	private String name;
	private String description;

	public BaseInfo<I> setCode() {
		this.code = UniqueUtil.shortUUID();
		return this;
	}
}
