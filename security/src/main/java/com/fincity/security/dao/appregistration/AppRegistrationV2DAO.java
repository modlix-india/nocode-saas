package com.fincity.security.dao.appregistration;

import org.jooq.types.ULong;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.fincity.security.dto.appregistration.AbstractAppRegistration;
import com.fincity.security.enums.AppRegistrationObjectType;
import com.fincity.security.enums.ClientLevelType;

import reactor.core.publisher.Mono;

@Service
public class AppRegistrationV2DAO {

    public Mono<? extends AbstractAppRegistration> create(AppRegistrationObjectType type,
            AbstractAppRegistration entity) {
        return Mono.empty();
    }

    public Mono<? extends AbstractAppRegistration> getById(AppRegistrationObjectType type, ULong id) {
        return Mono.empty();
    }

    public Mono<Boolean> delete(AppRegistrationObjectType type, ULong id) {
        return Mono.empty();
    }

    public Mono<Page<? extends AbstractAppRegistration>> get(AppRegistrationObjectType type, ULong appId,
            ULong clientId, String clientType, ClientLevelType level, String businessType, Pageable pageable) {
        return Mono.empty();
    }
}
