package com.fincity.security.service;

import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.jooq.service.AbstractJOOQDataService;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.security.dao.ClientManagerDAO;
import com.fincity.security.dto.ClientManager;
import com.fincity.security.jooq.tables.records.SecurityClientManagerRecord;

import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class ClientManagerService
        extends AbstractJOOQDataService<SecurityClientManagerRecord, ULong, ClientManager, ClientManagerDAO> {

    @Autowired
    private SecurityMessageResourceService messageResourceService;

	@PreAuthorize("hasAuthority('Authorities.Client_CREATE')")
    @Override
    public Mono<ClientManager> create(ClientManager entity) {
        return FlatMapUtil.flatMapMono(
                () -> this.dao.readByClientIdAndManagerId(entity.getClientId(), entity.getManagerId())
                        .hasElement(),
                exists -> {
                    if (Boolean.TRUE.equals(exists))
                        return this.messageResourceService.throwMessage(
                                msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                                SecurityMessageResourceService.CLIENT_MANAGER_ALREADY_EXISTS);

                    return Mono.just(entity);
                },
                (exists, entityToCreate) -> super.create(entityToCreate))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ClientManagerService.create"));
    }
}
