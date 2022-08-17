package com.fincity.security.dao;

import static com.fincity.security.jooq.tables.SecurityClientUrl.SECURITY_CLIENT_URL;

import org.jooq.Field;
import org.jooq.types.ULong;
import org.springframework.stereotype.Component;

import com.fincity.security.dto.ClientUrl;
import com.fincity.security.jooq.tables.records.SecurityClientUrlRecord;

@Component
public class ClientUrlDAO extends AbstractClientCheckDAO<SecurityClientUrlRecord, ULong, ClientUrl> {

	public ClientUrlDAO() {
		super(ClientUrl.class, SECURITY_CLIENT_URL, SECURITY_CLIENT_URL.ID);
	}

	@Override
	protected Field<ULong> getClientIDField() {
		return SECURITY_CLIENT_URL.CLIENT_ID;
	}
}
