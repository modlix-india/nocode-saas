package com.fincity.saas.commons.jooq.flow.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public abstract class AbstractFlowUpdatableDTO<I extends Serializable, U extends Serializable> extends AbstractFlowDTO<I, U> {

	@Serial
	private static final long serialVersionUID = 295036657353428449L;
}
