package com.fincity.security.service;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
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

    @Autowired
    private SecurityMessageResourceService messageResourceService;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private CacheService cacheService;

    @Autowired
    private ClientService clientService;

    public Mono<Boolean> isUserManagerForClient(ULong userId, ULong clientId) {
        String cacheKey = clientId + ":" + userId;
        return this.cacheService.cacheValueOrGet(
                CACHE_NAME_CLIENT_MANAGER,
                () -> this.readByClientIdAndManagerId(clientId, userId)
                        .hasElement()
                        .defaultIfEmpty(false),
                cacheKey);
    }

    public Mono<Boolean> isUserOwnerOrManagerForClient(ContextAuthentication ca, ULong clientId) {
        if (ca == null || ca.getUser() == null) 
            return Mono.just(false);

        boolean isOwner = checkIfUserIsOwner(ca);
        if (isOwner) 
            return Mono.just(true);

        ULong userId = ULong.valueOf(ca.getUser().getId());
        return FlatMapUtil.flatMapMono(
                () -> this.isUserManagerForClient(userId, clientId),
                isManager -> {
                    if (Boolean.TRUE.equals(isManager))
                        return Mono.just(true);

                    return this.clientService.isBeingManagedBy(
                            ULong.valueOf(ca.getUser().getClientId()), clientId);
                })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ClientManagerService.isUserOwnerOrManagerForClient"));
    }

    private static boolean checkIfUserIsOwner(ContextAuthentication ca) {
        if (ca == null || ca.getUser() == null) {
            return false;
        }

        Collection<? extends GrantedAuthority> authorities = ca.getUser().getAuthorities();
        if (authorities != null && !authorities.isEmpty()) {
            return SecurityContextUtil.hasAuthority(OWNER_ROLE, authorities);
        }

        List<String> stringAuthorities = ca.getUser().getStringAuthorities();
        if (stringAuthorities != null && !stringAuthorities.isEmpty()) {
            return SecurityContextUtil.hasAuthority(OWNER_ROLE, toGrantedAuthorities(stringAuthorities));
        }

        return false;
    }

    private static Collection<? extends GrantedAuthority> toGrantedAuthorities(List<String> stringAuthorities) {
        if (stringAuthorities == null || stringAuthorities.isEmpty()) {
            return Set.of();
        }
        return stringAuthorities.parallelStream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toSet());
    }

    private Mono<ClientManager> readByClientIdAndManagerId(ULong clientId, ULong managerId) {
        return this.dao.readByClientIdAndManagerId(clientId, managerId);
    }

    private Mono<Boolean> evictClientManagerCache(ULong clientId, ULong managerId) {
        String cacheKey = clientId + ":" + managerId;
        return this.cacheService.evict(CACHE_NAME_CLIENT_MANAGER, cacheKey);
    }

    @PreAuthorize("hasAuthority('Authorities.Client_CREATE')")
    @Override
    public Mono<ClientManager> create(ClientManager entity) {
        return FlatMapUtil.flatMapMono(
                        () -> this.readByClientIdAndManagerId(entity.getClientId(), entity.getManagerId())
                                .hasElement(),
                        exists -> {
                            if (Boolean.TRUE.equals(exists))
                                return this.messageResourceService.throwMessage(
                                        msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                                        SecurityMessageResourceService.CLIENT_MANAGER_ALREADY_EXISTS);

                            return Mono.just(entity);
                        },
                        (exists, entityToCreate) -> super.create(entityToCreate),
                        (exists, entityToCreate, created) -> {
                            this.evictClientManagerCache(entityToCreate.getClientId(), entityToCreate.getManagerId())
                                    .subscribe();
                            return Mono.just(created);
                        })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ClientManagerService.create"));
    }

    @PreAuthorize("hasAuthority('Authorities.Client_CREATE')")
    @Override
    public Mono<Integer> delete(ULong id) {
        return FlatMapUtil.flatMapMono(
                        () -> this.read(id),
                        clientManager -> this.dao.hasAnyOtherClientAssociations(clientManager.getManagerId(), id),
                        (clientManager, hasOtherAssociations) -> super.delete(id),
                        (clientManager, hasOtherAssociations, deleted) -> this.evictClientManagerCache(
                                        clientManager.getClientId(), clientManager.getManagerId())
                                .map(x -> deleted),
                        (clientManager, hasOtherAssociations, deleted, evicted) -> {
                            if (!Boolean.TRUE.equals(hasOtherAssociations)) {
                                return this.tokenService
                                        .deleteTokens(
                                                clientManager.getManagerId().toBigInteger())
                                        .map(x -> deleted);
                            }
                            return Mono.just(deleted);
                        })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ClientManagerService.delete"));
    }
}
