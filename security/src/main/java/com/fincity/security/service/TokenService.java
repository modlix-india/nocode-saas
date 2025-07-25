package com.fincity.security.service;

import org.jooq.types.ULong;
import org.springframework.stereotype.Service;

import com.fincity.saas.commons.jooq.service.AbstractJOOQDataService;
import com.fincity.saas.commons.security.jwt.ContextUser;
import com.fincity.saas.commons.security.service.IAuthenticationService;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.service.CacheService;
import com.fincity.security.dao.TokenDAO;
import com.fincity.security.dto.TokenObject;
import com.fincity.security.jooq.tables.records.SecurityUserTokenRecord;

import reactor.core.publisher.Mono;

import java.math.BigInteger;

@Service
public class TokenService extends AbstractJOOQDataService<SecurityUserTokenRecord, ULong, TokenObject, TokenDAO> {

    private final CacheService cacheService;

    public TokenService(CacheService cacheService) {
        this.cacheService = cacheService;
    }

    public Mono<Integer> evictTokensOfUser(ULong id) {
        return this.dao
                .getTokensOfId(id)
                .flatMap(e ->
                {
                    logger.debug("Evicting : {}", e);
                    return cacheService.evict(IAuthenticationService.CACHE_NAME_TOKEN, e);
                })
                .collectList()
                .map(e -> 1);
    }

    @Override
    protected Mono<ULong> getLoggedInUserId() {
        return SecurityContextUtil.getUsersContextUser().map(ContextUser::getId).map(ULong::valueOf);
    }

    public Mono<Integer> deleteTokens(BigInteger id) {
        ULong ulId = ULong.valueOf(id);

        return this.evictTokensOfUser(ulId)
                .flatMap(e -> this.dao.deleteAllTokens(ulId));
    }
}
