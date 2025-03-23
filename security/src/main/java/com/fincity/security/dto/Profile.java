package com.fincity.security.dto;

import java.util.Map;

import org.jooq.types.ULong;

import com.fincity.saas.commons.model.dto.AbstractUpdatableDTO;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;

@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@ToString(callSuper = true)
public class Profile extends AbstractUpdatableDTO<ULong, ULong> {

	private static final long serialVersionUID = -7176719786090846975L;

	private ULong appId;
	private ULong clientId;
	private String name;
	private String description;

	// A profile can be inherited or can be created from scratch.
	// If the profile is not inherited, we shall depend on client hierarcy to
	// determine the overrides.
	// Leading up to root profileId.
	// But this rootProfileId will always be assigned to the user not the profile
	// id which is inherited from rootProfileId.
	private ULong rootProfileId;

	private Map<String, Object> arrangement;
}