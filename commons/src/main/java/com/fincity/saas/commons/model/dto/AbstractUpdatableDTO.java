package com.fincity.saas.commons.model.dto;

import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class AbstractUpdatableDTO<I extends Serializable, U extends Serializable> extends AbstractDTO<I, U> {

	private static final long serialVersionUID = -2901530029048605138L;

	private LocalDateTime updatedAt;
	private U updatedBy;
}
