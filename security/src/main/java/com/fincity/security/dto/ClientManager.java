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
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ClientManager extends AbstractDTO<ULong, ULong> {

	@Serial
	private static final long serialVersionUID = 8201030542976518773L;

	private ULong clientId;
	private ULong managerId;
}
