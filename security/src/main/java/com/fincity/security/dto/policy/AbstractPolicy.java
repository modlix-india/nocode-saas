package com.fincity.security.dto.policy;

import java.io.Serial;

import org.jooq.types.ULong;

import com.fasterxml.jackson.annotation.JsonProperty;
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

	@JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
	private ULong clientId;

	@JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
	private ULong appId;

	private Short noFailedAttempts;

	@JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
	private Long userLockTimeMin;

	public abstract String generate();

}
