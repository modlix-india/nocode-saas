package com.fincity.saas.commons.jooq.flow.dto;

import com.fincity.saas.commons.jooq.flow.FlowField;
import com.fincity.saas.commons.model.dto.AbstractUpdatableDTO;
import com.google.gson.JsonObject;
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
public abstract class AbstractFlowUpdatableDTO<I extends Serializable, U extends Serializable> extends AbstractUpdatableDTO<I, U> {

	@Serial
	private static final long serialVersionUID = 295036657353428449L;

	private String appCode;
	private String clientCode;

	@FlowField
	private JsonObject fields;
}
