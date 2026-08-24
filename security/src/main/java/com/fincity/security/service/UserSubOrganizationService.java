package com.fincity.security.service;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.jooq.util.ULongUtil;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.security.jwt.ContextUser;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.service.CacheService;
import com.fincity.saas.commons.util.BooleanUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.security.dao.UserDAO;
import com.fincity.security.dto.User;
import com.fincity.security.jooq.enums.SecuritySoxLogObjectName;
import com.fincity.security.jooq.tables.records.SecurityUserRecord;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;
import reactor.util.function.Tuples;

@Service
public class UserSubOrganizationService
        extends AbstractSecurityUpdatableDataService<SecurityUserRecord, ULong, User, UserDAO> {

    private static final String USER_SUB_ORG = "userSubOrg";

    private static final String OWNER_ROLE = "Authorities.ROLE_Owner";

    private static final String OWNER = "owner";

    private final SecurityMessageResourceService msgService;

    private final CacheService cacheService;

    private final TokenService tokenService;

    private final OrgStructureService orgStructureService;

    private ClientService clientService;

    private UserService userService;

    public UserSubOrganizationService(
            SecurityMessageResourceService msgService,
            CacheService cacheService,
            TokenService tokenService,
            OrgStructureService orgStructureService) {
        this.msgService = msgService;
        this.cacheService = cacheService;
        this.tokenService = tokenService;
        this.orgStructureService = orgStructureService;
    }

    private static boolean isOwner(List<String> authorities) {
        return SecurityContextUtil.hasAuthority(OWNER_ROLE, toGrantedAuthorities(authorities));
    }

    private static boolean isOwner(Collection<? extends GrantedAuthority> authorities) {
        return SecurityContextUtil.hasAuthority(OWNER_ROLE, authorities);
    }

    private static Collection<? extends GrantedAuthority> toGrantedAuthorities(List<String> stringAuthorities) {

        if (stringAuthorities == null || stringAuthorities.isEmpty())
            return Set.of();

        return stringAuthorities.parallelStream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toSet());
    }

    @Lazy
    @Autowired
    private void setClientService(ClientService clientService) {
        this.clientService = clientService;
    }

    @Lazy
    @Autowired
    private void setUserService(UserService userService) {
        this.userService = userService;
    }

    protected String getCacheName() {
        return USER_SUB_ORG;
    }

    protected String getCacheKey(Object... entityNames) {
        return String.join(
                ":",
                Stream.of(entityNames)
                        .filter(Objects::nonNull)
                        .map(Object::toString)
                        .toArray(String[]::new));
    }

    /**
     * Drops the owner list and the client's whole reporting tree.
     *
     * <p>The tree eviction is unconditional and comes first, because every caller of this has
     * changed something about a user: created them, changed their reporting line, or changed their
     * status. Any of those changes the tree, and a stale tree is what sends a notification to
     * somebody who has left.
     */
    public Mono<Boolean> evictOwnerCache(ULong clientId, ULong userId) {

        Mono<Boolean> tree = this.orgStructureService.evict(clientId);

        if (userId == null)
            return tree.then(this.cacheService.evict(this.getCacheName(), this.getCacheKey(clientId, OWNER)));

        return tree.then(Mono.zip(
                this.cacheService.evict(this.getCacheName(), this.getCacheKey(clientId, OWNER)),
                this.cacheService.evict(this.getCacheName(), this.getCacheKey(clientId, userId)))
                .map(evicted -> evicted.getT1() && evicted.getT2()));
    }

    private <T> Mono<T> forbiddenError(String message, Object... params) {
        return msgService
                .getMessage(message, params)
                .handle((msg, sink) -> sink.error(new GenericException(HttpStatus.FORBIDDEN, msg)));
    }

    @Override
    protected Mono<ULong> getLoggedInUserId() {
        return SecurityContextUtil.getUsersContextUser().map(ContextUser::getId).map(ULong::valueOf);
    }

    @Override
    public SecuritySoxLogObjectName getSoxObjectName() {
        return SecuritySoxLogObjectName.USER;
    }

    @Override
    protected String describeEntity(User entity) {
        if (entity == null)
            return null;
        return entity.getEmailId() != null && !entity.getEmailId().isBlank()
                ? entity.getEmailId()
                : entity.getUserName();
    }

    @Override
    protected ULong resolveClientId(User entity) {
        return entity.getClientId();
    }

    private Mono<Integer> evictTokens(ULong id) {
        return this.tokenService.evictTokensOfUser(id);
    }

    @Override
    protected Mono<User> updatableEntity(User entity) {
        return this.read(entity.getId()).map(e -> {
            e.setReportingTo(entity.getReportingTo());
            return e;
        });
    }

    @PreAuthorize("hasAuthority('Authorities.User_UPDATE')")
    public Mono<User> updateManager(ULong userId, ULong managerId) {

        return FlatMapUtil.flatMapMono(() -> this.dao.readById(userId), user -> {
            if (user.getReportingTo() != null && user.getReportingTo().equals(managerId))
                return Mono.just(user);
            return this.updateManager(user, managerId);
        })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "UserService.updateReportingManager"));
    }

    private Mono<User> updateManager(User user, ULong managerId) {

        return FlatMapUtil.flatMapMono(
                SecurityContextUtil::getUsersContextAuthentication,
                ca -> Mono.just(user),
                (ContextAuthentication ca, User uUser) -> ca.isSystemClient()
                        ? Mono.just(Boolean.TRUE)
                        : clientService
                                .isUserClientManageClient(ca, uUser.getClientId())
                                .<Boolean>flatMap(BooleanUtil::safeValueOfWithEmpty),
                (ca, uUser, sysOrManaged) -> this.canReportTo(uUser.getClientId(), managerId, uUser.getId())
                        .flatMap(canReport -> !BooleanUtil.safeValueOf(canReport)
                                ? this.msgService.throwMessage(
                                        msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                                        SecurityMessageResourceService.USER_REPORTING_ERROR)
                                : Mono.just(uUser)),
                (ContextAuthentication ca, User uUser, Boolean sysOrManaged, User validUser) -> {
                    return super.update(validUser.setReportingTo(managerId))
                            .flatMap(updated -> this.evictHierarchyCaches(updated, validUser.getReportingTo(),
                                    managerId));
                },
                (ca, uUser, sysOrManaged, validUser, updated) -> this.evictTokens(updated.getId())
                        .<User>map(evicted -> updated))
                .switchIfEmpty(
                        this.forbiddenError(SecurityMessageResourceService.FORBIDDEN_UPDATE, "user reporting manager"));
    }

    public Mono<Boolean> evictManagerCaches(ULong clientId, ULong oldManagerId, ULong newManagerId) {

        // Ahead of the guard below: a user moving to or from "reports to nobody" leaves both ids
        // null on one side, and the tree still changed.
        Mono<Boolean> tree = this.orgStructureService.evict(clientId);

        if (oldManagerId == null && newManagerId == null)
            return tree;

        return tree.thenMany(Flux.fromIterable(Stream.of(oldManagerId, newManagerId).filter(Objects::nonNull).toList()))
                .flatMap(managerId -> this.cacheService.evict(getCacheName(), getCacheKey(clientId, managerId)))
                .then(Mono.just(Boolean.TRUE))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "UserSubOrganizationService.evictManagerCaches"));
    }

    private Mono<User> evictHierarchyCaches(User updatedUser, ULong oldReportingTo, ULong newReportingTo) {

        Mono<Boolean> tree = this.orgStructureService.evict(updatedUser.getClientId());

        if (oldReportingTo == null && newReportingTo == null)
            return tree.thenReturn(updatedUser);

        return tree.thenMany(
                Flux.fromIterable(Stream.of(oldReportingTo, newReportingTo).filter(Objects::nonNull).toList()))
                .flatMap(managerId -> this.cacheService.evict(getCacheName(),
                        getCacheKey(updatedUser.getClientId(), managerId)))
                .then(Mono.just(updatedUser))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "UserSubOrganizationService.evictHierarchyCaches"));
    }

    public Mono<Boolean> canReportTo(ULong clientId, ULong reportingTo, ULong userId) {

        if (reportingTo == null)
            return Mono.just(Boolean.TRUE);

        return this.dao.canReportTo(clientId, reportingTo, userId);
    }

    public Flux<ULong> getCurrentUserSubOrg() {

        return SecurityContextUtil.getUsersContextAuthentication()
                .flatMapMany(ca -> this.getSubOrg(
                        ULongUtil.valueOf(ca.getUser().getClientId()),
                        ULongUtil.valueOf(ca.getUser().getId()),
                        isOwner(ca.getAuthorities())))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "UserService.getCurrentUserSubOrg"));
    }

    public Flux<ULong> getUserSubOrgInternal(String appCode, ULong clientId, ULong userId) {

        return this.isOwner(appCode, clientId, userId)
                .flatMapMany(isOwner -> this.getSubOrg(clientId, userId, isOwner))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "UserService.getUserSubOrgInternal"));
    }

    @PreAuthorize("hasAuthority('Authorities.User_READ')")
    public Flux<ULong> getUserSubOrg(String appCode, ULong clientId, ULong userId, ULong managerId) {

        return this.isOwner(appCode, clientId, userId)
                .flatMapMany(isOwner -> this.getSubOrg(clientId, userId, managerId, isOwner))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "UserService.getUserSubOrg"));
    }

    private Mono<Boolean> isOwner(String appCode, ULong clientId, ULong userId) {
        return this.userService.getUserAuthorities(appCode, clientId, userId).map(UserSubOrganizationService::isOwner);
    }

    private Flux<ULong> getSubOrg(ULong clientId, ULong userId, Boolean isOwner) {

        return this.getSubOrg(clientId, userId, userId, isOwner)
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "UserService.getSubOrg [clientId, userId]"));
    }

    private Flux<ULong> getSubOrg(ULong clientId, ULong userId, ULong managerId, Boolean isOwner) {

        if (managerId == null || managerId.equals(userId))
            return this.getSubOrgUserIds(clientId, userId, Boolean.TRUE, isOwner)
                    .contextWrite(
                            Context.of(LogUtil.METHOD_NAME, "UserService.getSubOrg [clientId, userId, managerId]"));

        return FlatMapUtil.flatMapMono(
                () -> this.dao.readById(managerId),
                user -> this.clientService.doesClientManageClient(clientId, user.getClientId()),
                (user, isManaged) -> Boolean.TRUE.equals(isManaged)
                        ? Mono.just(Tuples.of(user.getClientId(), managerId))
                        : this.forbiddenError(
                                SecurityMessageResourceService.FORBIDDEN_PERMISSION,
                                "user reporting hierarchy"))
                .flatMapMany(tuple -> this.getSubOrgUserIds(tuple.getT1(), tuple.getT2(), Boolean.TRUE, isOwner))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "UserService.getSubOrg [clientId, userId, managerId]"));
    }

    private Flux<ULong> getSubOrgUserIds(ULong clientId, ULong userId, boolean includeSelf, boolean isOwner) {

        if (isOwner)
            return this.getAllUserIds(clientId)
                    .flatMapMany(Flux::fromIterable)
                    .contextWrite(Context.of(LogUtil.METHOD_NAME, "UserSubOrganizationService.getSubOrgUserIds"));

        // One cached tree, walked in memory. This used to expandDeep over getLevel1SubOrg, which
        // issued a cache lookup per person in the sub-tree: fine for one flat client, hundreds of
        // round trips per deal read once an organisation is eight or ten levels deep. The result is
        // the same set, including deactivated people, because the query behind it never filtered on
        // status and reads depend on that.
        return this.orgStructureService
                .getOrgStructure(clientId)
                .flatMapMany(tree -> Flux.fromIterable(tree.subOrg(userId, includeSelf)))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "UserSubOrganizationService.getSubOrgUserIds"));
    }

    /**
     * This person and everyone they report to, transitively.
     *
     * <p>The inverse of {@link #getSubOrgUserIds}: the people whose sub-org contains this user, and
     * therefore the people entitled to their records. Bounded by the depth of the tree rather than
     * its size, which is why it is worth asking this way round.
     */
    public Flux<ULong> getSelfAndManagers(ULong clientId, ULong userId) {

        return this.orgStructureService
                .getOrgStructure(clientId)
                .flatMapMany(tree -> Flux.fromIterable(tree.selfAndManagers(userId)))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "UserSubOrganizationService.getSelfAndManagers"));
    }

    private Mono<List<ULong>> getAllUserIds(ULong clientId) {

        return this.cacheService.cacheValueOrGet(
                this.getCacheName(),
                () -> this.dao.getUserIdsByClientId(clientId, null, false).collectList(),
                this.getCacheKey(clientId, OWNER));
    }

    private Flux<User> mapIdsToUser(Flux<ULong> idsFlux) {
        return idsFlux.collectList()
                .flatMapMany(ids -> (ids == null || ids.isEmpty())
                        ? Flux.empty()
                        : this.userService.readByIds(ids, null).flatMapMany(Flux::fromIterable))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "UserSubOrganizationService.mapIdsToResponses"));
    }

    public Flux<User> getCurrentUserSubOrgUsers() {
        return this.mapIdsToUser(this.getCurrentUserSubOrg())
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "UserService.getCurrentUserSubOrgUsers"));
    }

    public Flux<User> getUserSubOrgInternalUsers(String appCode, ULong clientId, ULong userId) {
        return this.mapIdsToUser(this.getUserSubOrgInternal(appCode, clientId, userId))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "UserService.getUserSubOrgInternalUsers"));
    }

    @PreAuthorize("hasAuthority('Authorities.User_READ')")
    public Flux<User> getUserSubOrgUsers(String appCode, ULong clientId, ULong userId, ULong managerId) {
        return this.mapIdsToUser(this.getUserSubOrg(appCode, clientId, userId, managerId))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "UserService.getUserSubOrgUsers"));
    }
}
