package com.fincity.security.service;

import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fincity.saas.common.security.jwt.ContextUser;
import com.fincity.saas.common.security.util.SecurityContextUtil;
import com.fincity.saas.commons.jooq.service.AbstractJOOQDataService;
import com.fincity.saas.commons.service.CacheService;
import com.fincity.security.dao.TokenDAO;
import com.fincity.security.dto.TokenObject;
import com.fincity.security.jooq.tables.records.SecurityUserTokenRecord;

import reactor.core.publisher.Mono;

@Service
public class TokenService extends AbstractJOOQDataService<SecurityUserTokenRecord, ULong, TokenObject, TokenDAO> {

	@Autowired
	private CacheService cacheService;

	public static final String CACHE_NAME_TOKEN = "tokenCache";

	public Mono<Integer> evictTokensOfUser(ULong id) {

		return this.dao.getTokensOfId(id).flatMap(e -> cacheService.evict(CACHE_NAME_TOKEN, e)).collectList()
				.map(e -> 1);
	}

	@Override
	protected Mono<ULong> getLoggedInUserId() {

		return SecurityContextUtil.getUsersContextUser().map(ContextUser::getId).map(ULong::valueOf);
	}
}
