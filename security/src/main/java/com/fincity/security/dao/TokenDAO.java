package com.fincity.security.dao;

import static com.fincity.security.jooq.tables.SecurityUserToken.SECURITY_USER_TOKEN;

import org.jooq.types.ULong;
import org.springframework.stereotype.Component;

import com.fincity.saas.commons.jooq.dao.AbstractDAO;
import com.fincity.security.dto.TokenObject;
import com.fincity.security.jooq.tables.records.SecurityUserTokenRecord;

@Component
public class TokenDAO extends AbstractDAO<SecurityUserTokenRecord, ULong, TokenObject> {

	protected TokenDAO() {
		super(TokenObject.class, SECURITY_USER_TOKEN, SECURITY_USER_TOKEN.ID);
	}

}
