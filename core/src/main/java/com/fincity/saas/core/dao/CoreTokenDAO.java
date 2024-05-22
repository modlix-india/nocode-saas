package com.fincity.saas.core.dao;

import static com.fincity.saas.core.jooq.tables.CoreTokens.CORE_TOKENS;

import org.jooq.types.ULong;
import org.springframework.stereotype.Service;

import com.fincity.saas.commons.jooq.dao.AbstractDAO;
import com.fincity.saas.core.dto.CoreToken;
import com.fincity.saas.core.jooq.tables.records.CoreTokensRecord;

import reactor.core.publisher.Mono;

@Service
public class CoreTokenDAO extends AbstractDAO<CoreTokensRecord, ULong, CoreToken> {

	protected CoreTokenDAO() {
		super(CoreToken.class, CORE_TOKENS, CORE_TOKENS.ID);
	}

}
