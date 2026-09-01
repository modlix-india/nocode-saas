package com.fincity.security.dto;

import org.jooq.types.ULong;

import com.fincity.saas.commons.model.dto.AbstractUpdatableDTO;
import com.fincity.security.enums.ClientUrlType;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;

@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@ToString(callSuper = true)
public class ClientUrl extends AbstractUpdatableDTO<ULong, ULong> {

	private static final long serialVersionUID = 2962225494941959699L;

	private ULong clientId;
	private String urlPattern;
	private String appCode;

	/**
	 * LIVE serves the published app; DRAFT serves its draft surface. Null is read
	 * as LIVE, so every existing row keeps its meaning with no backfill.
	 */
	private ClientUrlType urlType = ClientUrlType.LIVE;
}
