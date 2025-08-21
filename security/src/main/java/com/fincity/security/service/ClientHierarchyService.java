package com.fincity.security.service;

import java.util.List;

import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.jooq.service.AbstractJOOQDataService;
import com.fincity.saas.commons.service.CacheService;
import com.fincity.security.dao.ClientHierarchyDAO;
import com.fincity.security.dto.ClientHierarchy;
import com.fincity.security.jooq.tables.records.SecurityClientHierarchyRecord;

import lombok.Getter;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class ClientHierarchyService
        extends AbstractJOOQDataService<SecurityClientHierarchyRecord, ULong, ClientHierarchy, ClientHierarchyDAO> {

    private static final String CACHE_NAME_CLIENT_HIERARCHY = "clientHierarchy";
    private static final String CACHE_NAME_USER_CLIENT_HIERARCHY = "userClientHierarchy";
    private final SecurityMessageResourceService securityMessageResourceService;
    private final CacheService cacheService;
    @Getter
    private ClientService clientService;

    public ClientHierarchyService(SecurityMessageResourceService securityMessageResourceService,
                                  CacheService cacheService) {
        this.securityMessageResourceService = securityMessageResourceService;
        this.cacheService = cacheService;
    }

    @Autowired
    public void setClientService(@Lazy ClientService clientService) {
        this.clientService = clientService;
    }

    public Mono<ClientHierarchy> create(ULong managingClientId, ULong clientId) {

        return FlatMapUtil.flatMapMono(

                        () -> {
                            if (clientId.equals(managingClientId))
                                return Mono.empty();

                            return Mono.just(Boolean.TRUE);
                        },
                        areSame -> this.getClientHierarchy(managingClientId),
                        (areSame, manageClientHie) -> {

                            if (!manageClientHie.canAddLevel())
                                return Mono.empty();

                            ClientHierarchy clientHierarchy = new ClientHierarchyBuilder(clientId)
                                    .next(managingClientId)
                                    .next(manageClientHie.getManageClientLevel0())
                                    .next(manageClientHie.getManageClientLevel1())
                                    .next(manageClientHie.getManageClientLevel2())
                                    .next(manageClientHie.getManageClientLevel3())
                                    .build();

                            return this.create(clientHierarchy);
                        })
                .flatMap(e -> this.cacheService.put(CACHE_NAME_CLIENT_HIERARCHY, e, clientId))
                .switchIfEmpty(this.securityMessageResourceService.throwMessage(
                        msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                        SecurityMessageResourceService.FORBIDDEN_CREATE, "ClientHierarchy"));
    }

    public Mono<ClientHierarchy> getClientHierarchy(ULong clientId) {
        return this.cacheService.cacheValueOrGet(CACHE_NAME_CLIENT_HIERARCHY,
                () -> this.dao.getClientHierarchy(clientId), clientId);
    }

    public Mono<ClientHierarchy> getUserClientHierarchy(ULong userId) {
        return this.cacheService.cacheValueOrGet(CACHE_NAME_USER_CLIENT_HIERARCHY,
                () -> this.dao.getUserClientHierarchy(userId), userId);
    }

    public Flux<ULong> getClientHierarchyIds(ULong clientId) {
        return this.getClientHierarchy(clientId)
                .flatMapMany(clientHierarchy -> Flux.fromIterable(clientHierarchy.getClientIds()));
    }

    public Mono<List<ULong>> getClientHierarchyIdInOrder(ULong clientId) {
        return this.getClientHierarchy(clientId)
                .map(ClientHierarchy::getClientIdsInOrder);
    }

    public Mono<Boolean> isBeingManagedBy(ULong managingClientId, ULong clientId) {

        if (managingClientId.equals(clientId))
            return Mono.just(Boolean.TRUE);

        return this.getClientHierarchy(clientId)
                .flatMap(clientHierarchy -> Mono.just(clientHierarchy.isManagedBy(managingClientId)))
                .switchIfEmpty(Mono.just(Boolean.FALSE));
    }

    public Mono<Boolean> isBeingManagedBy(String managingClientCode, String clientCode) {

        if (managingClientCode.equals(clientCode))
            return Mono.just(Boolean.TRUE);

        return FlatMapUtil.flatMapMono(

                        () -> this.clientService.getClientId(clientCode),

                        clientId -> this.clientService.getClientId(managingClientCode),

                        (clientId, managingClientId) -> this.isBeingManagedBy(managingClientId, clientId))
                .switchIfEmpty(Mono.just(Boolean.FALSE));
    }

    public Mono<ULong> getManagingClient(ULong clientId, ClientHierarchy.Level level) {

        if (level.equals(ClientHierarchy.Level.SYSTEM))
            return this.clientService.getSystemClientId();

        return this.getClientHierarchy(clientId).mapNotNull(clientHierarchy -> clientHierarchy.getManagingClient(level));
    }

    public Mono<Boolean> isUserBeingManaged(ULong managingClientId, ULong userId) {
        return this.getUserClientHierarchy(userId)
                .flatMap(clientHierarchy -> Mono.just(clientHierarchy.isManagedBy(managingClientId)))
                .switchIfEmpty(Mono.just(Boolean.FALSE));
    }

    public Mono<Boolean> isUserBeingManaged(String managingClientCode, ULong userId) {

        return FlatMapUtil.flatMapMono(

                        () -> this.clientService.getClientId(managingClientCode),

                        managingClientId -> isUserBeingManaged(managingClientId, userId))
                .switchIfEmpty(Mono.just(Boolean.FALSE));
    }

    private static class ClientHierarchyBuilder {

        private final ClientHierarchy clientHierarchy;
        private int currentLevel = -1;
        private boolean isValid = Boolean.TRUE;

        public ClientHierarchyBuilder(ULong clientId) {
            if (clientId == null)
                throw new GenericException(HttpStatus.BAD_REQUEST, "Client Id cannot be null.");

            this.clientHierarchy = new ClientHierarchy().setClientId(clientId);
        }

        public ClientHierarchyBuilder next(ULong clientId) {

            if (!this.isValid)
                return this;

            if (clientId == null) {
                this.isValid = false;
                return this;
            }

            this.currentLevel++;

            switch (this.currentLevel) {
                case 0 -> this.clientHierarchy.setManageClientLevel0(clientId);
                case 1 -> this.clientHierarchy.setManageClientLevel1(clientId);
                case 2 -> this.clientHierarchy.setManageClientLevel2(clientId);
                case 3 -> this.clientHierarchy.setManageClientLevel3(clientId);
                default -> throw new GenericException(HttpStatus.BAD_REQUEST, "Invalid level.");
            }

            return this;
        }

        public ClientHierarchy build() {
            if (this.clientHierarchy.getManageClientLevel0() == null)
                throw new GenericException(HttpStatus.BAD_REQUEST, "Invalid Client hierarchy.");

            return this.clientHierarchy;
        }
    }

}
