package com.fincity.saas.commons.model.dto;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import org.springframework.data.annotation.Id;

import lombok.Data;
import lombok.ToString;
import lombok.experimental.Accessors;
import lombok.experimental.FieldNameConstants;

@Data
@Accessors(chain = true)
@ToString(callSuper = true)
@FieldNameConstants
public abstract class AbstractDTO<I extends Serializable, U extends Serializable> implements Serializable {

	@Serial
	private static final long serialVersionUID = 7628167781600904807L;

	@Id
	private I id;
	private LocalDateTime createdAt;
	private U createdBy;
}
