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

	private Short noFailedAttempts = 3;

	@JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
	private Long userLockTimeMin = 15L;

	public abstract String generate();

	public void initDefaults() {
		if (this.noFailedAttempts == null) {
			this.noFailedAttempts = 3;
		}
		if (this.userLockTimeMin == null) {
			this.userLockTimeMin = 15L;
		}
	}

}
