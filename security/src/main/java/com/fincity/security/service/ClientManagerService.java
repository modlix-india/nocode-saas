package com.fincity.security.service;

import java.util.Collection;
import java.util.List;

import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.jooq.service.AbstractJOOQDataService;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.service.CacheService;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.security.dao.ClientManagerDAO;
import com.fincity.security.dto.ClientManager;
import com.fincity.security.jooq.tables.records.SecurityClientManagerRecord;

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
    private UserService userService;

    public ClientManagerService(
            SecurityMessageResourceService messageResourceService,
            CacheService cacheService,
            ClientService clientService) {
        this.messageResourceService = messageResourceService;
        this.cacheService = cacheService;
        this.clientService = clientService;
    }

    private static boolean checkIfUserIsOwner(ContextAuthentication ca) {
        if (ca == null || ca.getUser() == null) return false;

        Collection<? extends GrantedAuthority> authorities = ca.getUser().getAuthorities();
        if (authorities != null && !authorities.isEmpty())
            return SecurityContextUtil.hasAuthority(OWNER_ROLE, authorities);

        List<String> stringAuthorities = ca.getUser().getStringAuthorities();
        return stringAuthorities != null && stringAuthorities.contains(OWNER_ROLE);
    }

    @Autowired
    @Lazy
    public void setUserService(UserService userService) {
        this.userService = userService;
    }

    private String buildCacheKey(ULong clientId, ULong userId) {
        return clientId + ":" + userId;
    }

    public Mono<Boolean> isUserManagerForClient(ULong userId, ULong clientId) {
        return this.cacheService.cacheValueOrGet(
                CACHE_NAME_CLIENT_MANAGER,
                () -> this.readByClientIdAndManagerId(clientId, userId)
                        .hasElement()
                        .defaultIfEmpty(Boolean.FALSE),
                this.buildCacheKey(clientId, userId));
    }

    public Mono<Boolean> isUserOwnerOrManagerForClient(ContextAuthentication ca, ULong clientId) {
        if (ca == null || ca.getUser() == null) return Mono.just(Boolean.FALSE);

        if (checkIfUserIsOwner(ca)) return Mono.just(Boolean.TRUE);

        ULong userId = ULong.valueOf(ca.getUser().getId());
        ULong userClientId = ULong.valueOf(ca.getUser().getClientId());

        return this.isUserManagerForClient(userId, clientId)
                .flatMap(isManager -> Boolean.TRUE.equals(isManager)
                        ? Mono.just(Boolean.TRUE)
                        : this.clientService.isBeingManagedBy(userClientId, clientId))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ClientManagerService.isUserOwnerOrManagerForClient"));
    }

    private Mono<ClientManager> readByClientIdAndManagerId(ULong clientId, ULong managerId) {
        return this.dao.readByClientIdAndManagerId(clientId, managerId);
    }

    private Mono<Boolean> evictClientManagerCache(ULong clientId, ULong managerId) {
        return this.cacheService.evict(CACHE_NAME_CLIENT_MANAGER, this.buildCacheKey(clientId, managerId));
    }

    private Mono<ClientManager> validateCreatePermissions(
            ContextAuthentication ca, ClientManager entity, boolean exists) {
        if (exists)
            return this.messageResourceService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    SecurityMessageResourceService.CLIENT_MANAGER_ALREADY_EXISTS);

        return FlatMapUtil.flatMapMono(
                        () -> this.clientService.isBeingManagedBy(
                                ULong.valueOf(ca.getUser().getClientId()), entity.getClientId()),
                        isManagingClient -> Boolean.TRUE.equals(isManagingClient)
                                ? this.userService.readInternal(entity.getManagerId())
                                : this.messageResourceService.throwMessage(
                                        msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                                        SecurityMessageResourceService.FORBIDDEN_CREATE,
                                        "Client Manager"),
                        (isManagingClient, managerUser) ->
                                this.clientService.isBeingManagedBy(managerUser.getClientId(), entity.getClientId()),
                        (isManagingClient, managerUser, isManagerClientInHierarchy) ->
                                Boolean.FALSE.equals(isManagerClientInHierarchy)
                                        ? this.messageResourceService.throwMessage(
                                                msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                                                SecurityMessageResourceService.FORBIDDEN_CREATE,
                                                "Client Manager")
                                        : Mono.just(entity))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ClientManagerService.validateCreatePermissions"));
    }

    @PreAuthorize("hasAuthority('Authorities.Client_CREATE')")
    @Override
    public Mono<ClientManager> create(ClientManager entity) {
        return FlatMapUtil.flatMapMono(
                        SecurityContextUtil::getUsersContextAuthentication,
                        ca -> this.readByClientIdAndManagerId(entity.getClientId(), entity.getManagerId())
                                .hasElement(),
                        (ca, exists) -> this.validateCreatePermissions(ca, entity, exists),
                        (ca, exists, entityToCreate) -> super.create(entityToCreate),
                        (ca, exists, entityToCreate, created) -> this.evictClientManagerCache(
                                        created.getClientId(), created.getManagerId())
                                .thenReturn(created))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ClientManagerService.create"));
    }

    @PreAuthorize("hasAuthority('Authorities.Client_DELETE')")
    @Override
    public Mono<Integer> delete(ULong id) {
        return FlatMapUtil.flatMapMono(
                        SecurityContextUtil::getUsersContextAuthentication,
                        ca -> this.read(id),
                        (ca, clientManager) -> this.clientService.isBeingManagedBy(
                                ULong.valueOf(ca.getUser().getClientId()), clientManager.getClientId()),
                        (ca, clientManager, isManagingClient) -> Boolean.TRUE.equals(isManagingClient)
                                ? this.dao.hasAnyOtherClientAssociations(clientManager.getManagerId(), id)
                                : Mono.empty(),
                        (ca, clientManager, isManagingClient, hasOtherAssociations) -> super.delete(id),
                        (ca, clientManager, isManagingClient, hasOtherAssociations, deleted) ->
                                this.evictClientManagerCache(clientManager.getClientId(), clientManager.getManagerId())
                                        .thenReturn(deleted))
                .switchIfEmpty(this.messageResourceService.throwMessage(
                        msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                        SecurityMessageResourceService.FORBIDDEN_DELETE,
                        "Client Manager"))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ClientManagerService.delete"));
    }
}
