package com.fincity.saas.entity.processor.dto;

import java.io.Serial;

import org.jooq.types.ULong;

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
public class SubSource extends BaseDto<SubSource> {

	@Serial
	private static final long serialVersionUID = 6507017658445805056L;

	private ULong sourceId;
}
