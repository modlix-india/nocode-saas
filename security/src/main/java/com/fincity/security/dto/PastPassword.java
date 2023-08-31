package com.fincity.security.dto;

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
public class PastPassword extends AbstractDTO<ULong, ULong> {

	private static final long serialVersionUID = 4360853737062991688L;

	private ULong userId;
	private String password;
	private boolean passwordHashed;
}
