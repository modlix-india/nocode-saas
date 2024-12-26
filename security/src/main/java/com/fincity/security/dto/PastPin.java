package com.fincity.security.dto;

import java.io.Serial;

import org.jooq.types.ULong;

import com.fincity.saas.commons.model.dto.AbstractDTO;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = false)
@ToString(callSuper = true)
public class PastPin extends AbstractDTO<ULong, ULong> {

	@Serial
	private static final long serialVersionUID = 8415121154666644983L;

	private ULong userId;
	private String pin;
	private boolean pinHashed;
}
