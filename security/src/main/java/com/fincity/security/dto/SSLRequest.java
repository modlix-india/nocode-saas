package com.fincity.security.dto;

import org.jooq.types.ULong;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fincity.saas.commons.model.dto.AbstractUpdatableDTO;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
public class SSLRequest extends AbstractUpdatableDTO<ULong, ULong> {

	private static final long serialVersionUID = 8489013343984509243L;

	private ULong urlId;
	private String domains;
	private String organization;
	@JsonIgnore
	private String crtKey;
	private String csr;
	private Integer validity;
	private String failedReason;
}
