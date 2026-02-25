package com.fincity.security.service;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import org.jooq.types.ULong;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.jooq.service.AbstractJOOQDataService;
import com.fincity.saas.commons.jooq.util.ULongUtil;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.service.CacheService;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.security.dao.ClientManagerDAO;
import com.fincity.security.dto.Client;
import com.fincity.security.dto.ClientManager;
import com.fincity.security.jooq.tables.records.SecurityClientManagerRecord;

import java.util.List;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class ClientManagerService
        extends AbstractJOOQDataService<SecurityClientManagerRecord, ULong, ClientManager, ClientManagerDAO> {

    private static final String CACHE_NAME_CLIENT_MANAGER = "clientManager";
    private static final String OWNER_ROLE = "Authorities.ROLE_Owner";

    private final SecurityMessageResourceService messageResourceService;
    private final CacheService cacheService;
    private final ClientService clientService;
    private final UserService userService;
    private final ClientHierarchyService clientHierarchyService;

    public ClientManagerService(
            SecurityMessageResourceService messageResourceService,
            CacheService cacheService,
            ClientService clientService, @Lazy UserService userService, ClientHierarchyService clientHierarchyService) {
        this.messageResourceService = messageResourceService;
        this.cacheService = cacheService;
        this.clientService = clientService;
        this.userService = userService;
        this.clientHierarchyService = clientHierarchyService;
    }

    // This method is used to check if context user has access to make a user client
    // manager.
    private Mono<Boolean> checkAccess(ULong targetUserClientId) {

        return SecurityContextUtil.getUsersContextAuthentication()
                .flatMap(ca -> {

                    if (ca.isSystemClient())
                        return Mono.just(Boolean.TRUE);

                    ULong contextUserClientId = ULongUtil.valueOf(ca.getUser().getClientId());

                    if (contextUserClientId.equals(targetUserClientId))
                        return Mono.just(
                                SecurityContextUtil.hasAuthority(OWNER_ROLE, ca.getAuthorities()));

                    return this.clientHierarchyService.isClientBeingManagedBy(contextUserClientId, targetUserClientId);
                });
    }

    private Mono<Boolean> evictCacheForUserAndClient(ULong userId, ULong clientId) {
        return this.cacheService.evict(CACHE_NAME_CLIENT_MANAGER, userId, clientId);
    }

    @PreAuthorize("hasAuthority('Authorities.Client_UPDATE')")
    public Mono<Boolean> create(ULong userId, ULong clientId) {

        return FlatMapUtil.flatMapMono(

                SecurityContextUtil::getUsersContextAuthentication,

                ca -> this.userService.readInternal(userId),

                (ca, user) -> {

                    if (user.getClientId().equals(clientId))
                        return this.messageResourceService.throwMessage(
                                msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                                SecurityMessageResourceService.HIERARCHY_ERROR, "Client manager");

                    return Mono.just(Boolean.TRUE);
                },

                (ca, user, validated) -> this.checkAccess(user.getClientId()),

                (ca, user, validated, hasAccess) -> {

                    if (!Boolean.TRUE.equals(hasAccess))
                        return Mono.empty();

                    return this.dao.createIfNotExists(clientId, userId,
                            ULongUtil.valueOf(ca.getUser().getId()));
                },

                (ca, user, validated, hasAccess, result) -> this.evictCacheForUserAndClient(userId, clientId)
                        .thenReturn(Boolean.TRUE))

                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ClientManagerService.create"))
                .switchIfEmpty(this.messageResourceService.throwMessage(
                        msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                        SecurityMessageResourceService.FORBIDDEN_PERMISSION, "Client Manager CREATE"));
    }

    @PreAuthorize("hasAuthority('Authorities.Client_READ')")
    public Mono<Page<Client>> getClientsOfUser(ULong userId, Pageable pageable) {

        return FlatMapUtil.flatMapMono(

                () -> this.userService.readInternal(userId),

                user -> this.checkAccess(user.getClientId()),

                (user, hasAccess) -> {

                    if (!Boolean.TRUE.equals(hasAccess))
                        return Mono.empty();

                    return this.dao.getClientsOfManager(userId, pageable);
                },

                (user, hasAccess, page) -> Flux.fromIterable(page.getContent())
                        .flatMap(this.clientService::readInternal)
                        .collectList()
                        .map(clients -> (Page<Client>) PageableExecutionUtils.getPage(
                                clients, pageable, page::getTotalElements)))

                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ClientManagerService.getClientsOfUser"))
                .switchIfEmpty(this.messageResourceService.throwMessage(
                        msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                        SecurityMessageResourceService.FORBIDDEN_PERMISSION, "Client Manager READ"));
    }

    @PreAuthorize("hasAuthority('Authorities.Client_UPDATE')")
    public Mono<Boolean> delete(ULong userId, ULong clientId) {

        return FlatMapUtil.flatMapMono(

                () -> this.userService.readInternal(userId),

                user -> this.checkAccess(user.getClientId()),

                (user, hasAccess) -> {

                    if (!Boolean.TRUE.equals(hasAccess))
                        return Mono.empty();

                    return this.dao.deleteByClientIdAndManagerId(clientId, userId);
                },

                (user, hasAccess, result) -> this.evictCacheForUserAndClient(userId, clientId)
                        .thenReturn(Boolean.TRUE))

                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ClientManagerService.delete"))
                .switchIfEmpty(this.messageResourceService.throwMessage(
                        msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                        SecurityMessageResourceService.FORBIDDEN_PERMISSION, "Client Manager DELETE"));
    }

    public Mono<Boolean> isUserClientManager(ContextAuthentication ca, ULong targetClientId) {

        ULong userId = ULongUtil.valueOf(ca.getUser().getId());
        ULong userClientId = ULongUtil.valueOf(ca.getUser().getClientId());

        if (SecurityContextUtil.hasAuthority(OWNER_ROLE, ca.getAuthorities()))
            return Mono.just(Boolean.TRUE);

        if (userClientId.equals(targetClientId))
            return Mono.just(Boolean.FALSE);

        return this.cacheService.cacheValueOrGet(CACHE_NAME_CLIENT_MANAGER,
                () -> this.dao.isManagerForClient(userId, targetClientId),
                userId, targetClientId);
    }

    public Mono<Boolean> isUserClientManager(String appCode, ULong userId, ULong userClientId, ULong targetClientId) {

        return this.userService.getUserAuthorities(appCode, userClientId, userId)
                .flatMap(list -> {

                    if (SecurityContextUtil.hasAuthority(OWNER_ROLE, list))
                        return Mono.just(Boolean.TRUE);

                    if (userClientId.equals(targetClientId))
                        return Mono.just(Boolean.FALSE);

                    return this.cacheService.cacheValueOrGet(CACHE_NAME_CLIENT_MANAGER,
                            () -> this.dao.isManagerForClient(userId, targetClientId),
                            userId, targetClientId);
                });
    }

    public Mono<Map<ULong, Collection<ULong>>> getManagerIds(Set<ULong> clientIds) {
        return this.dao.getManagerIds(clientIds);
    }

    public Mono<Integer> createInternal(ULong clientId, ULong managerId, ULong createdBy) {
        return this.dao.createIfNotExists(clientId, managerId, createdBy);
    }
  
    public Mono<List<ULong>> getClientIdsOfManagerInternal(ULong managerId) {
        return this.dao
                .getClientIdsOfManager(managerId)
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ClientManagerService.getClientIdsOfManagerInternal"));
    }

    public Mono<List<ULong>> getClientIdsOfManagersInternal(List<ULong> managerIds) {
        return this.dao
                .getClientIdsOfManagers(managerIds)
                .contextWrite(Context.of(
                        LogUtil.METHOD_NAME, "ClientManagerService.getClientIdsOfManagersInternal"));
    }
}
