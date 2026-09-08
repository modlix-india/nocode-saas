package com.fincity.security.service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.jooq.types.ULong;
import org.springframework.stereotype.Service;

import com.fincity.saas.commons.service.CacheService;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.security.dao.UserDAO;
import com.fincity.security.dto.OrgStructure;
import com.fincity.security.jooq.enums.SecurityUserStatusCode;

import reactor.core.publisher.Mono;
import reactor.util.context.Context;

/**
 * Owns the cached reporting tree for a client.
 *
 * <p>One cache entry per client, replacing a cache entry per <i>edge</i>. The old shape
 * ({@code userSubOrg:<clientId>:<managerId>} holding one manager's direct reports) could only be
 * used by walking it, one lookup per person, and could not answer the upward question at all.
 *
 * <p>Kept deliberately small: load, cache, evict. The traversals are on {@link OrgStructure} so they
 * can be tested without a cache or a database in the way.
 *
 * <h2>Eviction is the contract</h2>
 *
 * <p>Every write that changes a reporting line, moves a user between clients, or changes a status
 * must call {@link #evict(ULong)}. Those call sites are listed on that method. A stale tree sends
 * notifications to people who have left, so this is not an optimisation detail.
 */
@Service
public class OrgStructureService {

    /**
     * Shares the name of the cache it replaces, so the existing eviction call sites keep working
     * while both shapes are in use and so a single {@code clear_cache} clears both.
     */
    public static final String CACHE_NAME = "userSubOrg";

    private static final String TREE_KEY_PREFIX = "orgStructure:";

    private final CacheService cacheService;
    private final UserDAO userDAO;

    public OrgStructureService(CacheService cacheService, UserDAO userDAO) {
        this.cacheService = cacheService;
        this.userDAO = userDAO;
    }

    public Mono<OrgStructure> getOrgStructure(ULong clientId) {

        if (clientId == null) return Mono.just(new OrgStructure(Map.of(), Set.of()));

        return this.cacheService
                .cacheValueOrGet(CACHE_NAME, () -> this.load(clientId), TREE_KEY_PREFIX + clientId)
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "OrgStructureService.getOrgStructure"));
    }

    private Mono<OrgStructure> load(ULong clientId) {

        Map<ULong, ULong> managerOf = new HashMap<>();
        Set<ULong> inactive = new HashSet<>();

        return this.userDAO
                .getOrgEdges(clientId)
                .doOnNext(record -> {
                    ULong userId = record.value1();
                    if (userId == null) return;
                    // Every user is a key, including those reporting to nobody: the map's key set is
                    // how contains() answers "is this person in this client at all".
                    managerOf.put(userId, record.value2());
                    SecurityUserStatusCode status = record.value3();
                    if (status != null && status != SecurityUserStatusCode.ACTIVE) inactive.add(userId);
                })
                .then(Mono.fromSupplier(() -> new OrgStructure(managerOf, inactive)));
    }

    /**
     * Drops a client's tree.
     *
     * <p>Called from every path that can change it:
     *
     * <ul>
     *   <li>{@code UserService.create} — a new user, with a reporting line
     *   <li>{@code UserService.update} — a changed reporting line, status or client
     *   <li>{@code UserSubOrganizationService.updateManager} — the explicit reassignment
     *   <li>{@code UserService.makeUserActive} / {@code makeUserInActive}
     *   <li>{@code UserInviteService.acceptInvite} — creates a user with a reporting line, and
     *       evicted nothing at all before this change
     * </ul>
     *
     * <p>When a user moves between clients, both the old and the new client must be evicted; the
     * caller knows both ids and this method does not, so it is called twice.
     */
    public Mono<Boolean> evict(ULong clientId) {

        if (clientId == null) return Mono.just(Boolean.TRUE);

        return this.cacheService
                .evict(CACHE_NAME, TREE_KEY_PREFIX + clientId)
                .map(evicted -> Boolean.TRUE)
                .defaultIfEmpty(Boolean.TRUE);
    }
}
