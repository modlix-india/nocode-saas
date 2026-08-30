package com.fincity.security.service;

import static com.fincity.saas.commons.util.StringUtil.*;
import static com.fincity.security.jooq.enums.SecuritySoxLogActionName.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.jooq.types.ULong;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.jooq.service.AbstractJOOQDataService;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.model.condition.ComplexCondition;
import com.fincity.saas.commons.model.condition.ComplexConditionOperator;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.commons.model.condition.FilterConditionOperator;
import com.fincity.saas.commons.security.jwt.ContextUser;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.util.BooleanUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.security.dao.UserDAO;
import com.fincity.security.dao.UserInviteDAO;
import com.fincity.security.dto.App;
import com.fincity.security.dto.AppProperty;
import com.fincity.security.dto.Client;
import com.fincity.security.dto.ClientHierarchy;
import com.fincity.security.dto.Designation;
import com.fincity.security.dto.Profile;
import com.fincity.security.dto.User;
import com.fincity.security.dto.UserInvite;
import com.fincity.security.enums.ClientLevelType;
import com.fincity.security.jooq.enums.SecuritySoxLogObjectName;
import com.fincity.security.jooq.enums.SecurityUserStatusCode;
import com.fincity.security.jooq.tables.records.SecurityUserInviteRecord;
import com.fincity.security.model.AuthenticationRequest;
import com.fincity.security.model.AuthenticationResponse;
import com.fincity.security.model.RegistrationResponse;
import com.fincity.security.model.UserRegistrationRequest;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

@Service
public class UserInviteService
        extends AbstractJOOQDataService<SecurityUserInviteRecord, ULong, UserInvite, UserInviteDAO> {

    private final SecurityMessageResourceService msgService;
    private final ClientService clientService;
    private final UserDAO userDao;
    private final AuthenticationService authenticationService;
    private final SoxLogService soxLogService;
    private final ProfileService profileService;
    private final AppService appService;
    private final ClientHierarchyService clientHierarchyService;
    private final ClientActivityService clientActivityService;
    private final OrgStructureService orgStructureService;
    private final DesignationService designationService;

    public UserInviteService(SecurityMessageResourceService msgService, ClientService clientService,
            AuthenticationService authenticationService, UserDAO userDao, SoxLogService soxLogService,
            ProfileService profileService, AppService appService, ClientHierarchyService clientHierarchyService,
            @org.springframework.context.annotation.Lazy ClientActivityService clientActivityService,
            OrgStructureService orgStructureService, DesignationService designationService) {

        this.msgService = msgService;
        this.clientService = clientService;
        this.userDao = userDao;
        this.authenticationService = authenticationService;
        this.soxLogService = soxLogService;
        this.profileService = profileService;
        this.appService = appService;
        this.clientHierarchyService = clientHierarchyService;
        this.clientActivityService = clientActivityService;
        this.orgStructureService = orgStructureService;
        this.designationService = designationService;
    }

    @PreAuthorize("hasAuthority('Authorities.User_CREATE')")
    public Mono<Map<String, Object>> createInvite(UserInvite entity) {

        return FlatMapUtil.flatMapMono(

                SecurityContextUtil::getUsersContextAuthentication,
                ca -> {
                    if (entity.getClientId() == null) {
                        entity.setClientId(ULong.valueOf(ca.getUser().getClientId()));
                        return Mono.just(entity);
                    }

                    return this.clientService
                            .isUserClientManageClient(ca, entity.getClientId())
                            .filter(BooleanUtil::safeValueOf)
                            .map(x -> entity);
                },

                (ca, invite) -> {

                    if (entity.getReportingTo() == null)
                        return Mono.just(Boolean.TRUE);

                    return this.userDao.readById(entity.getReportingTo())
                            .filter(user -> user.getClientId().equals(entity.getClientId()))
                            .map(x -> Boolean.TRUE);
                },
                (ca, invite, reportingToInSameClient) -> invite.getProfileId() == null
                        ? Mono.just(true)
                        : this.profileService
                                .hasAccessToProfiles(
                                        ULong.valueOf(ca.getUser()
                                                .getClientId()),
                                        Set.of(invite.getProfileId()))
                                .filter(BooleanUtil::safeValueOf),

                (ca, invite, reportingToInSameClient, hasAccess) -> {
                    String appCode = ca.getUrlAppCode();
                    if (StringUtil.safeIsBlank(appCode))
                        return Mono.just(true);

                    return this.appService.getAppByCode(appCode)
                            .flatMap(app -> this.validateUserCheckForInvite(
                                    app.getId(), app.getClientId(), entity.getClientId(),
                                    entity.getUserName(), entity.getEmailId(),
                                    entity.getPhoneNumber()))
                            .defaultIfEmpty(true);
                },

                (ca, invite, reportingToInSameClient, hasAccess, userCheckValid) -> this.userDao
                        .checkUserExistsForInvite(
                                entity.getClientId(),
                                entity.getUserName(),
                                entity.getEmailId(),
                                entity.getPhoneNumber())
                        .flatMap(exists -> {
                            if (exists)
                                return this.addUserProfile(entity);

                            invite.setInviteCode(
                                    UUID.randomUUID().toString().replace("-", ""));
                            return super.create(invite).flatMap(createdInvite -> {
                                clientActivityService.createLog(createdInvite.getClientId(),
                                        "User Invite Created",
                                        "User invite created for " + createdInvite.getEmailId());
                                Map<String, Object> result = new HashMap<>();
                                result.put("userRequest", createdInvite);
                                result.put("existingUser", Boolean.FALSE);
                                return Mono.just(result);
                            });
                        }))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "UserInviteService.create"))
                .switchIfEmpty(this.msgService.throwMessage(
                        msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                        SecurityMessageResourceService.FORBIDDEN_CREATE,
                        "User Invite"));
    }

    /**
     * {@code SECURITY_USER_INVITE} has a {@code CREATED_BY} column and every row in
     * it was null, so an invite could never say who sent it.
     * {@code AbstractJOOQDataService.create} clears {@code createdBy} and refills it
     * from this hook, which defaults to empty - this service simply never overrode
     * it. Setting the field on the entity beforehand does not work; the base clears
     * it first.
     */
    @Override
    protected Mono<ULong> getLoggedInUserId() {
        return SecurityContextUtil.getUsersContextUser().map(ContextUser::getId).map(ULong::valueOf);
    }

    public Mono<UserInvite> getUserInvitation(String code) {
        return this.dao.getUserInvitation(code);
    }

    public Mono<Boolean> deleteUserInvitation(String code) {
        return this.dao.deleteUserInvitation(code);
    }

    public Mono<RegistrationResponse> acceptInvite(UserRegistrationRequest userRequest, ServerHttpRequest request,
            ServerHttpResponse response) {
        return FlatMapUtil.flatMapMono(

                () -> this.dao.getUserInvitation(userRequest.getInviteCode()),

                userInvite -> this.createWithInvitation(userRequest, userInvite, request, response))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "UserInviteService.acceptInvite"))
                .switchIfEmpty(this.msgService.throwMessage(
                        msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                        SecurityMessageResourceService.FORBIDDEN_CREATE,
                        "User Invitation Error"));
    }

    public Mono<RegistrationResponse> createWithInvitation(UserRegistrationRequest request, UserInvite userInvite,
            ServerHttpRequest httpRequest, ServerHttpResponse response) {

        return FlatMapUtil.flatMapMono(

                SecurityContextUtil::getUsersContextAuthentication,

                ca -> this.createWithInvitationInternal(request, userInvite)
                        .flatMap(createdUser -> this.deleteUserInvitation(userInvite.getInviteCode())
                                .thenReturn(createdUser)),

                (ca, createdUser) -> this
                        .getClientAuthenticationResponse(request, createdUser.getId(),
                                request.getInputPass(), httpRequest, response)
                        .<RegistrationResponse>map(authResp -> new RegistrationResponse()
                                .setUserId(createdUser.getId())
                                .setCreated(true)
                                .setAuthentication(authResp)))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "UserInviteService.createWithInvitation"))
                .switchIfEmpty(this.msgService.throwMessage(
                        msg -> new GenericException(HttpStatus.FORBIDDEN, msg), "User"));
    }

    private Mono<AuthenticationResponse> getClientAuthenticationResponse(
            UserRegistrationRequest registrationRequest,
            ULong userId, String password, ServerHttpRequest request, ServerHttpResponse response) {

        AuthenticationRequest authRequest = new AuthenticationRequest().setUserId(userId);

        if (registrationRequest.getInputPassType() != null)
            return switch (registrationRequest.getInputPassType()) {
                case PASSWORD ->
                    this.authenticationService.authenticate(authRequest.setPassword(password),
                            request, response);
                case PIN -> this.authenticationService.authenticate(authRequest.setPin(password),
                        request, response);
                case OTP -> Mono.empty();
            };

        if (!safeIsBlank(registrationRequest.getSocialRegisterState()))
            return this.authenticationService.authenticateWSocial(
                    authRequest.setSocialRegisterState(
                            registrationRequest.getSocialRegisterState()),
                    request,
                    response);

        return Mono.empty();
    }

    private Mono<User> createWithInvitationInternal(UserRegistrationRequest request, UserInvite userInvite) {

        User user = request.getUser();

        String password = request.getInputPass(request.getPassType());
        user.setPassword(null);
        user.setPasswordHashed(false);
        user.setAccountNonExpired(true);
        user.setAccountNonLocked(true);
        user.setCredentialsNonExpired(true);
        user.setNoFailedAttempt((short) 0);
        user.setNoPinFailedAttempt((short) 0);
        user.setNoOtpFailedAttempt((short) 0);
        user.setStatusCode(SecurityUserStatusCode.ACTIVE);

        if (user.getFirstName() == null)
            user.setFirstName(userInvite.getFirstName());
        if (user.getLastName() == null)
            user.setLastName(userInvite.getLastName());

        if (!safeIsBlank(userInvite.getPhoneNumber()))
            user.setPhoneNumber(userInvite.getPhoneNumber());
        if (!safeIsBlank(userInvite.getEmailId()))
            user.setEmailId(userInvite.getEmailId());
        if (!safeIsBlank(userInvite.getUserName()))
            user.setUserName(userInvite.getUserName());

        user.setClientId(userInvite.getClientId());
        user.setDesignationId(userInvite.getDesignationId());
        user.setReportingTo(userInvite.getReportingTo());

        return FlatMapUtil.flatMapMono(
                () ->

                this.userDao.checkUserExists(user.getClientId(), user.getUserName(), user.getEmailId(),
                        user.getPhoneNumber(), null)
                        .filter(userExists -> !userExists).map(userExists -> Boolean.FALSE),

                userExists -> SecurityContextUtil.getUsersContextAuthentication()
                        .flatMap(ca -> this.appService.getAppByCode(ca.getUrlAppCode()))
                        .flatMap(app -> this.validateUserCheckForInvite(
                                app.getId(), app.getClientId(), user.getClientId(),
                                user.getUserName(), user.getEmailId(), user.getPhoneNumber())),

                (userExists, userCheckValid) -> this.userDao.create(user),

                (userExists, userCheckValid, createdUser) -> {
                    this.soxLogService.createLog(createdUser.getId(), CREATE,
                            SecuritySoxLogObjectName.USER, "User created: " + createdUser.getEmailId());
                    this.clientActivityService.createLog(createdUser.getClientId(),
                            "User Invite Accepted",
                            "User invite accepted, user created: " + createdUser.getEmailId());

                    return this.userDao
                            .setPassword(createdUser.getId(), createdUser.getId(), password,
                                    request.getPassType())
                            .map(result -> result > 0)
                            .flatMap(BooleanUtil::safeValueOfWithEmpty);
                },

                (userExists, userCheckValid, createdUser, passSet) -> (userInvite.getProfileId() != null)
                        ? profileService.hasAccessToProfiles(user.getClientId(),
                                Set.of(userInvite.getProfileId()))
                        : Mono.just(Boolean.FALSE),

                (userExists, userCheckValid, createdUser, passSet, hasAddableProfile) -> {

                    // This path creates a user with a client and a reporting line and, until now,
                    // evicted nothing at all. The new joiner's manager kept a cached sub-org that
                    // did not include them, so the manager could not see their deals until
                    // something unrelated happened to evict it. Every other user-creating path
                    // already evicted; this one was simply missed.
                    Mono<Boolean> evicted = this.orgStructureService.evict(createdUser.getClientId());

                    if (!BooleanUtil.safeValueOf(hasAddableProfile))
                        return evicted.thenReturn(createdUser);

                    return evicted.then(this.userDao
                            .addProfileToUser(createdUser.getId(),
                                    userInvite.getProfileId())
                            .map(e -> createdUser));
                })
                .contextWrite(Context.of(LogUtil.METHOD_NAME,
                        "UserInviteService.createWithInvitationInternal"))
                .switchIfEmpty(this.msgService.throwMessage(
                        msg -> new GenericException(HttpStatus.FORBIDDEN, msg), "User"));
    }

    private Mono<Boolean> validateUserCheckForInvite(
            ULong appId, ULong appClientId, ULong clientId,
            String userName, String emailId, String phoneNumber) {

        return this.appService.getProperties(null, appId, null, AppService.APP_PROP_USER_CHECK)
                .flatMap(props -> {

                    String checkValue = AppService.APP_PROP_USER_CHECK_DEFAULT;

                    if (props != null && !props.isEmpty()) {
                        for (Map.Entry<ULong, Map<String, AppProperty>> entry : props.entrySet()) {
                            Map<String, AppProperty> propMap = entry.getValue();
                            if (propMap != null) {
                                AppProperty prop = propMap.get(AppService.APP_PROP_USER_CHECK);
                                if (prop != null && !StringUtil.safeIsBlank(prop.getValue())) {
                                    checkValue = prop.getValue();
                                    break;
                                }
                            }
                        }
                    }

                    if (AppService.APP_PROP_USER_CHECK_DEFAULT.equals(checkValue)
                            || StringUtil.safeIsBlank(checkValue))
                        return Mono.just(true);

                    final String check = checkValue;

                    return FlatMapUtil.flatMapMono(

                            () -> this.clientService.getClientLevelType(clientId, appId),

                            level -> this.clientHierarchyService.getClientHierarchy(clientId),

                            (level, hierarchy) -> resolveAndCheckDuplicate(
                                    check, level, hierarchy, appClientId,
                                    userName, emailId, phoneNumber))
                            .contextWrite(Context.of(LogUtil.METHOD_NAME,
                                    "UserInviteService.validateUserCheckForInvite"))
                            .defaultIfEmpty(true);
                })
                .defaultIfEmpty(true);
    }

    private Mono<Boolean> resolveAndCheckDuplicate(
            String check, ClientLevelType level, ClientHierarchy hierarchy, ULong appClientId,
            String userName, String emailId, String phoneNumber) {

        ULong parentId;
        boolean directChildren;
        boolean grandChildren;

        switch (check) {

            case AppService.APP_PROP_USER_CHECK_NO_DUP_CLIENT:
                if (level != ClientLevelType.CLIENT)
                    return Mono.just(true);

                parentId = appClientId;
                directChildren = true;
                grandChildren = false;
                break;

            case AppService.APP_PROP_USER_CHECK_NO_DUP_CUSTOMER:
                if (level != ClientLevelType.CUSTOMER && level != ClientLevelType.CONSUMER)
                    return Mono.just(true);

                if (appClientId.equals(hierarchy.getManageClientLevel1()))
                    parentId = hierarchy.getManageClientLevel0();
                else if (appClientId.equals(hierarchy.getManageClientLevel2()))
                    parentId = hierarchy.getManageClientLevel1();
                else if (appClientId.equals(hierarchy.getManageClientLevel3()))
                    parentId = hierarchy.getManageClientLevel2();
                else
                    return Mono.just(true);

                directChildren = true;
                grandChildren = true;
                break;

            case AppService.APP_PROP_USER_CHECK_NO_DUP_CONSUMER:
                if (level != ClientLevelType.CONSUMER)
                    return Mono.just(true);

                if (appClientId.equals(hierarchy.getManageClientLevel2()))
                    parentId = hierarchy.getManageClientLevel0();
                else if (appClientId.equals(hierarchy.getManageClientLevel3()))
                    parentId = hierarchy.getManageClientLevel1();
                else
                    return Mono.just(true);

                directChildren = true;
                grandChildren = false;
                break;

            default:
                return Mono.just(true);
        }

        return this.userDao.checkUserExistsUnderManagingClient(
                parentId, directChildren, grandChildren,
                userName, emailId, phoneNumber, null)
                .flatMap(exists -> {
                    if (Boolean.TRUE.equals(exists))
                        return this.msgService.throwMessage(
                                msg -> new GenericException(HttpStatus.CONFLICT, msg),
                                SecurityMessageResourceService.USER_ALREADY_EXISTS,
                                userName != null ? userName : emailId);

                    return Mono.just(true);
                });
    }

    private Mono<Map<String, Object>> addUserProfile(UserInvite invite) {

        if (invite.getProfileId() == null)
            return Mono.empty();

        return FlatMapUtil.flatMapMono(

                () -> this.userDao.getUserForInvite(
                        invite.getClientId(), invite.getUserName(), invite.getEmailId(),
                        invite.getPhoneNumber()),

                user -> this.profileService
                        .hasAccessToProfiles(user.getClientId(), Set.of(invite.getProfileId()))
                        .filter(BooleanUtil::safeValueOf),

                (user, hasAccessToProfiles) -> this.userDao
                        .addProfileToUser(user.getId(), invite.getProfileId())
                        .flatMap(e -> Mono.just(Map.of("userRequest", invite, "existingUser",
                                Boolean.TRUE))));
    }

    /**
     * Paged, filtered list of pending invites.
     * <p>
     * A {@code UserInvite} carries a live {@code inviteCode}, and
     * {@code POST /api/security/users/acceptInvite} is a permitted route - so
     * anyone holding an invite code can create the invited account with the
     * profile attached to it. Listing invites therefore hands out capabilities,
     * not just contact details, and needs the same authority that creating an
     * invite needs to be worth having.
     * <p>
     * Tenant scoping is enforced in {@code UserInviteDAO.filter(...)}, which ANDs
     * the caller's own client and the clients they manage into the WHERE clause of
     * both the row query and the count query. The client id comes from the signed
     * in user's context authentication, never from a caller supplied header.
     */
    @PreAuthorize("hasAuthority('Authorities.User_READ')")
    public Mono<Page<UserInvite>> getAllInvitedUsers(Pageable pageable, AbstractCondition condition) {
        return this.getAllInvitedUsers(pageable, condition, null);
    }

    /**
     * @param appId optional: keep only invites whose profile belongs to this app.
     *              An invite has no app column of its own, so this is resolved
     *              here rather than expressed as a caller-supplied condition.
     */
    @PreAuthorize("hasAuthority('Authorities.User_READ')")
    public Mono<Page<UserInvite>> getAllInvitedUsers(Pageable pageable, AbstractCondition condition, ULong appId) {
        return this.withAppFilter(condition, appId)
                .flatMap(finalCondition -> this.readPageFilter(pageable, finalCondition))
                .flatMap(page -> this.fillDetails(page.getContent()).thenReturn(page))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "UserInviteService.getAllInvitedUsers"));
    }

    private Mono<AbstractCondition> withAppFilter(AbstractCondition condition, ULong appId) {

        if (appId == null || appId.longValue() == 0L)
            return condition == null ? Mono.just(new ComplexCondition().setConditions(List.of())
                    .setOperator(ComplexConditionOperator.AND)) : Mono.just(condition);

        return this.dao.profileIdsOfApp(appId).map(profileIds -> {

            // An app with no profiles matches no invites. Saying so with an id that
            // cannot exist is clearer than an empty IN list, which different
            // databases treat differently.
            AbstractCondition appCondition = new FilterCondition()
                    .setField("profileId")
                    .setOperator(FilterConditionOperator.IN)
                    .setMultiValue(profileIds.isEmpty() ? List.of(ULong.valueOf(0)) : profileIds);

            return condition == null ? appCondition : ComplexCondition.and(condition, appCondition);
        });
    }

    /**
     * Resolves the display names an invite row is made of. Every field on
     * {@code security_user_invite} except the person's own name and contact
     * details is a foreign key, so a listing that shows the stored values shows
     * the reader a column of numbers.
     * <p>
     * Each lookup is a cached {@code readInternal} keyed by id, and the ids are
     * de-duplicated first - a page of ten invites from one client costs one
     * client read, not ten.
     * <p>
     * None of this can drop an invite. The only {@code filter} is inside
     * {@link #resolveNames}, where it discards a map ENTRY whose name did not
     * resolve; the invites themselves are mutated in place and returned whole.
     * That is deliberate: {@code UserService.fillDetails} filters the row flux
     * instead, so its {@code fetchCreatedBy}, {@code fetchDesignation} and
     * {@code fetchReportingTo} options silently delete every user that lacks the
     * id being fetched.
     */
    private Mono<List<UserInvite>> fillDetails(List<UserInvite> invites) {

        if (invites == null || invites.isEmpty())
            return Mono.just(invites == null ? List.of() : invites);

        return Mono.zip(
                this.resolveNames(invites, UserInvite::getClientId,
                        id -> this.clientService.getClientInfoById(id).map(Client::getName)),

                this.resolveNames(invites, UserInvite::getProfileId,
                        id -> this.profileService.readInternal(id).map(Profile::getName)),

                this.resolveNames(invites, UserInvite::getProfileId,
                        id -> this.profileService.readInternal(id)
                                .flatMap(profile -> this.appService.getAppByIdInternal(profile.getAppId()))
                                .map(App::getAppCode)),

                this.resolveNames(invites, UserInvite::getDesignationId,
                        id -> this.designationService.readInternal(id).map(Designation::getName)),

                this.resolveNames(invites, UserInvite::getReportingTo, this::userDisplayName),

                this.resolveNames(invites, UserInvite::getCreatedBy, this::userDisplayName))

                .map(names -> {

                    invites.forEach(invite -> invite
                            .setClientName(nameOf(names.getT1(), invite.getClientId()))
                            .setProfileName(nameOf(names.getT2(), invite.getProfileId()))
                            .setAppCode(nameOf(names.getT3(), invite.getProfileId()))
                            .setDesignationName(nameOf(names.getT4(), invite.getDesignationId()))
                            .setReportingToName(nameOf(names.getT5(), invite.getReportingTo()))
                            .setCreatedByName(nameOf(names.getT6(), invite.getCreatedBy())));

                    return invites;
                })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "UserInviteService.fillDetails"));
    }

    /**
     * Most invites carry no designation and no reporting line, so most of these
     * ids are null - and a null key is exactly what {@code Map.of().get(...)}
     * throws on.
     */
    private static String nameOf(Map<ULong, String> names, ULong id) {
        return id == null ? null : names.get(id);
    }

    private Mono<Map<ULong, String>> resolveNames(List<UserInvite> invites,
            Function<UserInvite, ULong> idOf, Function<ULong, Mono<String>> nameOf) {

        Set<ULong> ids = invites.stream()
                .map(idOf)
                .filter(id -> id != null && id.longValue() != 0L)
                .collect(Collectors.toSet());

        if (ids.isEmpty())
            return Mono.just(Map.of());

        return Flux.fromIterable(ids)
                .flatMap(id -> nameOf.apply(id)
                        .filter(name -> !StringUtil.safeIsBlank(name))
                        .map(name -> Tuples.of(id, name)))
                .collectMap(Tuple2::getT1, Tuple2::getT2);
    }

    /**
     * A person's name for display. Falls back to the user name and then the email
     * so a row never reads as blank, and never returns the {@link User} itself -
     * the record carries password and pin hashes.
     */
    private Mono<String> userDisplayName(ULong userId) {

        return this.userDao.readInternal(userId).map(user -> {

            String name = ((safeIsBlank(user.getFirstName()) ? "" : user.getFirstName())
                    + " "
                    + (safeIsBlank(user.getLastName()) ? "" : user.getLastName())).trim();

            if (!name.isEmpty())
                return name;

            if (!safeIsBlank(user.getUserName()))
                return user.getUserName();

            return safeIsBlank(user.getEmailId()) ? "" : user.getEmailId();
        });
    }

    /**
     * Revokes a pending invite on behalf of an administrator.
     * <p>
     * {@link #deleteUserInvitation(String)} deliberately has no checks - it is the
     * step that consumes an invite once {@code acceptInvite} has already proved the
     * caller holds the code. Reached from the outside it is a different thing
     * entirely: an invite code is a capability, and deleting someone else's invite
     * destroys their pending access. So the exposed route needs the authority that
     * creating an invite needs, and the same tenant gate
     * ({@code isUserClientManageClient}) that {@link #createInvite} applies - a
     * caller may only revoke inside their own client or a client they manage.
     * <p>
     * An unknown code is refused the same way an unauthorised one is, so the route
     * cannot be used to test whether a code exists.
     */
    @PreAuthorize("hasAnyAuthority('Authorities.User_CREATE', 'Authorities.User_DELETE')")
    public Mono<Boolean> revokeInvitation(String code) {

        return FlatMapUtil.flatMapMono(

                SecurityContextUtil::getUsersContextAuthentication,

                ca -> this.dao.getUserInvitation(code),

                (ca, invite) -> this.clientService.isUserClientManageClient(ca, invite.getClientId())
                        .filter(BooleanUtil::safeValueOf),

                (ca, invite, hasAccess) -> this.dao.deleteUserInvitation(code)
                        .map(deleted -> {
                            if (Boolean.TRUE.equals(deleted))
                                this.clientActivityService.createLog(invite.getClientId(),
                                        "User Invite Revoked",
                                        "User invite revoked for " + invite.getEmailId());
                            return deleted;
                        }))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "UserInviteService.revokeInvitation"))
                .switchIfEmpty(this.msgService.throwMessage(
                        msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                        SecurityMessageResourceService.FORBIDDEN_DELETE,
                        "User Invite"));
    }

}
