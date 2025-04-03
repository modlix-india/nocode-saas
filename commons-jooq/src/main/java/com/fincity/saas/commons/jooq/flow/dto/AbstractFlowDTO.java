package com.fincity.saas.commons.jooq.flow.dto;

import java.io.Serial;
import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

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
public abstract class AbstractFlowDTO<I extends Serializable, U extends Serializable> extends com.fincity.saas.commons.model.dto.AbstractDTO<I, U> {

	@Serial
	private static final long serialVersionUID = 7121981370061595384L;

	private String appCode;
	private String clientCode;
	private Map<String, ? extends Serializable> dynamicField = new LinkedHashMap<>();

}
