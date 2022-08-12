package com.fincity.security.dto;

import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class AbstractUpdatableDTO<I extends Serializable, U extends Serializable> extends AbstractDTO<I, U> {

	private static final long serialVersionUID = -2901530029048605138L;

	private LocalDateTime updatedAt;
	private U updatedBy;
}
