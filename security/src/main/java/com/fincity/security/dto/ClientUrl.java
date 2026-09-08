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

	/**
	 * Who this URL belongs to, by code and name rather than only by id.
	 *
	 * Read-only and filled in by {@code ClientUrlDAO.getClientUrls}, which
	 * already joins the client, so a screen listing an app's URLs across clients
	 * can label each row without a second call per distinct client -- 527 of them
	 * on cxapp.
	 *
	 * SECURITY_CLIENT_URL has no such columns, and that is fine on the write
	 * path: {@code AbstractDAO.create} builds the record with
	 * {@code rec.from(pojo)}, which maps only fields whose names match a column
	 * and ignores the rest. Every other read returns them null.
	 */
	private String clientCode;

	private String clientName;
}
