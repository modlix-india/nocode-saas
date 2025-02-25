package com.fincity.saas.notification.dto.base;

import java.io.Serial;

import org.jooq.types.ULong;

import com.fincity.saas.commons.model.dto.AbstractUpdatableDTO;
import com.fincity.saas.commons.util.UniqueUtil;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;

@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@ToString(callSuper = true)
public abstract class BaseIds<I extends BaseIds<I>> extends AbstractUpdatableDTO<ULong, ULong> {

	@Serial
	private static final long serialVersionUID = 4613264208588269380L;

	private ULong clientId;
	private ULong appId;
	private String code = UniqueUtil.shortUUID();
}
