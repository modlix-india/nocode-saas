package com.fincity.security.service.appregistration;

import org.jooq.types.ULong;

import com.fincity.saas.commons.model.dto.AbstractDTO;
import com.fincity.security.enums.AppRegistrationObjectType;

import reactor.core.publisher.Mono;

public interface IAppRegistrationHelperService {

    Mono<? extends AbstractDTO<ULong, ULong>> readObject(ULong id, AppRegistrationObjectType type);

    Mono<Boolean> hasAccessTo(ULong id, ULong clientId, AppRegistrationObjectType type);
}
