package com.fincity.security.service;

import java.util.UUID;

import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fincity.saas.commons.jooq.service.AbstractJOOQDataService;
import com.fincity.security.dao.OneTimeTokenDAO;
import com.fincity.security.dto.OneTimeToken;
import com.fincity.security.jooq.tables.records.SecurityOneTimeTokenRecord;

import reactor.core.publisher.Mono;

@Service
public class OneTimeTokenService
        extends AbstractJOOQDataService<SecurityOneTimeTokenRecord, ULong, OneTimeToken, OneTimeTokenDAO> {

    @Override
    public Mono<OneTimeToken> create(OneTimeToken entity) {
        return super.create(entity.setToken(UUID.randomUUID().toString().replace("-", "")));
    }

    /**
     * How long a minted one-time token can be redeemed. These are SSO handoff strings, redeemed
     * within seconds of minting by a redirect; five minutes is generous for a slow network.
     */
    @Value("${security.oneTimeToken.maxAgeMinutes:5}")
    private int maxAgeMinutes = 5;

    public Mono<OneTimeToken> getOneTimeToken(String token) {
        return this.dao.readOneTimeTokenAndDeleteBy(token, this.maxAgeMinutes);
    }
}
