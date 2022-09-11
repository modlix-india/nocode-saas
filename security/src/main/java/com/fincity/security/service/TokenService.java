package com.fincity.security.service;

import org.jooq.types.ULong;
import org.springframework.stereotype.Service;

import com.fincity.saas.common.security.jwt.ContextUser;
import com.fincity.saas.common.security.util.SecurityContextUtil;
import com.fincity.saas.commons.jooq.service.AbstractJOOQDataService;
import com.fincity.security.dao.TokenDAO;
import com.fincity.security.dto.TokenObject;
import com.fincity.security.jooq.tables.records.SecurityUserTokenRecord;

import reactor.core.publisher.Mono;

@Service
public class TokenService extends AbstractJOOQDataService<SecurityUserTokenRecord, ULong, TokenObject, TokenDAO> {

	@Override
	protected Mono<ULong> getLoggedInUserId() {

		return SecurityContextUtil.getUsersContextUser()
		        .map(ContextUser::getId)
		        .map(ULong::valueOf);
	}
}
