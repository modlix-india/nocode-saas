package com.fincity.security.dto.policy;

import java.io.Serial;

import org.jooq.types.ULong;
import org.jooq.types.UShort;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fincity.saas.commons.model.dto.AbstractUpdatableDTO;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public abstract class AbstractPolicy extends AbstractUpdatableDTO<ULong, ULong> {

	@Serial
	private static final long serialVersionUID = 234658377111974288L;

	private ULong clientId;
	private ULong appId;
	private UShort noFailedAttempts;
	private ULong userLockTimeMin;

	@JsonIgnore
	public abstract String generate();

}
