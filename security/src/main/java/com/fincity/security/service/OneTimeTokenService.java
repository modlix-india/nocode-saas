package com.fincity.security.service;

import com.fincity.saas.commons.jooq.service.AbstractJOOQDataService;
import com.fincity.security.dao.OneTimeTokenDAO;
import com.fincity.security.dto.OneTimeToken;
import com.fincity.security.jooq.tables.records.SecurityOneTimeTokenRecord;
import org.jooq.types.ULong;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Service
public class OneTimeTokenService extends AbstractJOOQDataService<SecurityOneTimeTokenRecord, ULong, OneTimeToken, OneTimeTokenDAO> {

    @Override
    public Mono<OneTimeToken> create(OneTimeToken entity) {
        return super.create(entity.setToken(UUID.randomUUID().toString().replace("-", "")));
    }

    public Mono<ULong> getUserId(String token) {
        return this.dao.readAndDeleteBy(token);
    }
}
