package com.fincity.security.service;

import static com.fincity.security.jooq.enums.SecuritySoxLogActionName.CREATE;

import java.net.InetSocketAddress;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fincity.security.dto.*;
import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.fincity.nocode.kirun.engine.util.string.StringFormatter;
import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.configuration.service.AbstractMessageService;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.jooq.util.ULongUtil;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.commons.model.condition.FilterConditionOperator;
import com.fincity.saas.commons.mq.events.EventCreationService;
import com.fincity.saas.commons.mq.events.EventNames;
import com.fincity.saas.commons.mq.events.EventQueObject;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.security.jwt.ContextUser;
import com.fincity.saas.commons.security.jwt.JWTUtil;
import com.fincity.saas.commons.security.jwt.JWTUtil.JWTGenerateTokenParameters;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.service.CacheService;
import com.fincity.saas.commons.util.BooleanUtil;
import com.fincity.saas.commons.util.CommonsUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.security.dao.UserDAO;
import com.fincity.security.dao.appregistration.AppRegistrationV2DAO;
import com.fincity.security.enums.otp.OtpPurpose;
import com.fincity.security.jooq.enums.SecuritySoxLogActionName;
import com.fincity.security.jooq.enums.SecuritySoxLogObjectName;
import com.fincity.security.jooq.enums.SecurityUserStatusCode;
import com.fincity.security.jooq.tables.records.SecurityUserRecord;
import com.fincity.security.model.AuthenticationIdentifierType;
import com.fincity.security.model.AuthenticationPasswordType;
import com.fincity.security.model.AuthenticationRequest;
import com.fincity.security.model.ClientRegistrationRequest;
import com.fincity.security.model.RequestUpdatePassword;
import com.fincity.saas.commons.security.model.UserResponse;
import com.fincity.security.model.otp.OtpGenerationRequestInternal;
import com.fincity.security.model.otp.OtpVerificationRequest;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuple3;
import reactor.util.function.Tuples;

@Service
public class UserService extends AbstractSecurityUpdatableDataService<SecurityUserRecord, ULong, User, UserDAO> {

    private static final String ASSIGNED_ROLE = " Role is assigned to the user ";
    private static final String UNASSIGNED_ROLE = " Role is removed from the selected user";

    private static final String CACHE_NAME_USER_ROLE = "userRoles";

    private static final String CACHE_NAME_USER = "user";

    private static final int VALIDITY_MINUTES = 30;

    private final ClientService clientService;
    private final AppService appService;
    private final ClientHierarchyService clientHierarchyService;
    private final PasswordEncoder passwordEncoder;
    private final SecurityMessageResourceService securityMessageResourceService;
    private final SoxLogService soxLogService;
    private final OtpService otpService;
    private final TokenService tokenService;
    private final EventCreationService ecService;
    private final AppRegistrationV2DAO appRegistrationDAO;
    private final ProfileService profileService;
    private final DepartmentService departmentService;
    private final DesignationService designationService;
    private final CacheService cacheService;
    private final RoleV2Service roleService;

    private UserSubOrganizationService userSubOrgService;

    @Value("${jwt.key}")
    private String tokenKey;

    public UserService(
            ClientService clientService,
            AppService appService,
            ClientHierarchyService clientHierarchyService,
            PasswordEncoder passwordEncoder,
            SecurityMessageResourceService securityMessageResourceService,
            SoxLogService soxLogService,
            OtpService otpService,
            TokenService tokenService,
            EventCreationService ecService,
            AppRegistrationV2DAO appRegistrationDAO,
            ProfileService profileService,
            DepartmentService departmentService,
            DesignationService designationService,
            CacheService cacheService,
            RoleV2Service roleService) {

        this.clientService = clientService;
        this.appService = appService;
        this.clientHierarchyService = clientHierarchyService;
        this.passwordEncoder = passwordEncoder;
        this.securityMessageResourceService = securityMessageResourceService;
        this.soxLogService = soxLogService;
        this.otpService = otpService;
        this.tokenService = tokenService;
        this.ecService = ecService;
        this.appRegistrationDAO = appRegistrationDAO;
        this.profileService = profileService;
        this.departmentService = departmentService;
        this.designationService = designationService;
        this.cacheService = cacheService;
        this.roleService = roleService;
    }

    @Lazy
    @Autowired
    private void setUserSubOrgService(UserSubOrganizationService userSubOrgService) {
        this.userSubOrgService = userSubOrgService;
    }

    private <T> Mono<T> forbiddenError(String message, Object... params) {
        return securityMessageResourceService
                .getMessage(message, params)
                .handle((msg, sink) -> sink.error(new GenericException(HttpStatus.FORBIDDEN, msg)));
    }

    public Mono<Tuple3<Client, Client, User>> findNonDeletedUserNClient(
            String userName,
            ULong userId,
            String clientCode,
            String appCode,
            AuthenticationIdentifierType authenticationIdentifierType) {
        return this.findUserNClient(
                userName,
                userId,
                clientCode,
                appCode,
                authenticationIdentifierType,
                this.getNonDeletedUserStatusCodes());
    }

    public Mono<Tuple3<Client, Client, User>> findUserNClient(
            String userName,
            ULong userId,
            String clientCode,
            String appCode,
            AuthenticationIdentifierType authenticationIdentifierType,
            SecurityUserStatusCode... userStatusCodes) {

        return FlatMapUtil.flatMapMono(
                        () -> this.dao
                                .getUsersBy(
                                        userName,
                                        userId,
                                        clientCode,
                                        appCode,
                                        authenticationIdentifierType,
                                        userStatusCodes)
                                .flatMap(users -> Mono.justOrEmpty(users.size() != 1 ? null : users.getFirst()))
                                .flatMap(user -> this.setAllAuthorities(appCode, user)),
                        user -> this.clientService.getActiveClient(user.getClientId()),
                        (user, uClient) -> uClient.getCode().equals(clientCode)
                                ? Mono.just(uClient)
                                : this.clientService.getClientBy(clientCode),
                        (user, uClient, mClient) -> Mono.just(Tuples.of(mClient, uClient, user)))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "UserService.findUserNClient"));
    }

    public Mono<List<String>> getUserAuthorities(String appCode, ULong clientId, ULong userId) {

        return FlatMapUtil.flatMapMono(
                        () -> cacheService
                                .cacheValueOrGet(
                                        CACHE_NAME_USER_ROLE,
                                        () -> this.roleService.getRoleAuthoritiesPerApp(userId),
                                        userId)
                                .map(map -> {
                                    List<String> appAuths = map.get(appCode);
                                    List<String> defaultAuths = map.getOrDefault("", new ArrayList<>());

                                    if (appAuths == null || appAuths.isEmpty()) return defaultAuths;
                                    if (defaultAuths.isEmpty()) return new ArrayList<>(appAuths);
                                    return Stream.of(appAuths.stream(), defaultAuths.stream())
                                            .flatMap(e -> e)
                                            .collect(Collectors.toCollection(ArrayList::new));
                                }),
                        roleAuths -> this.profileService
                                .getProfileAuthorities(appCode, clientId, userId)
                                .map(auths -> {
                                    roleAuths.addAll(auths);
                                    roleAuths.add("Authorities.Logged_IN");
                                    return roleAuths;
                                }))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "UserService.getUserAuthorities"));
    }

    private Mono<User> setAllAuthorities(String appCode, User user) {

        return this.getUserAuthorities(appCode, user.getClientId(), user.getId())
                .map(user::setAuthorities);
    }

    public Mono<Boolean> checkUserAndClient(Tuple3<Client, Client, User> userNClient, String clientCode) {

        if (clientCode == null) return Mono.just(Boolean.FALSE);

        return Mono.just(
                ContextAuthentication.CLIENT_TYPE_SYSTEM.equals(
                        userNClient.getT1().getTypeCode())
                        || clientCode.equals(userNClient.getT1().getCode())
                        || userNClient
                        .getT1()
                        .getId()
                        .equals(userNClient.getT2().getId())
                        ? Boolean.TRUE
                        : Boolean.FALSE);
    }

    public Mono<Boolean> checkUserStatus(User user, SecurityUserStatusCode... userStatusCodes) {

        return Mono.justOrEmpty(user)
                .filter(u -> u != null && userStatusCodes.length > 0)
                .map(User::getStatusCode)
                .map(statusCode -> Arrays.asList(userStatusCodes).contains(statusCode))
                .defaultIfEmpty(Boolean.FALSE);
    }

    public Mono<User> getUserForContext(String appCode, ULong id) {

        return FlatMapUtil.flatMapMono(() -> this.dao.readById(id), user -> this.setAllAuthorities(appCode, user))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "UserService.getUserForContext"));
    }

    public Mono<Short> increaseFailedAttempt(ULong userId, AuthenticationPasswordType passwordType) {
        return this.dao.increaseFailedAttempt(userId, passwordType);
    }

    public Mono<Boolean> resetFailedAttempt(ULong userId, AuthenticationPasswordType passwordType) {
        return this.dao.resetFailedAttempt(userId, passwordType);
    }

    public Mono<Short> increaseResendAttempt(ULong userId) {
        return this.dao.increaseResendAttempts(userId);
    }

    public Mono<Boolean> resetResendAttempt(ULong userId) {
        return this.dao.resetResendAttempts(userId);
    }

    public SecurityUserStatusCode[] getNonDeletedUserStatusCodes() {
        return new SecurityUserStatusCode[]{
                SecurityUserStatusCode.ACTIVE,
                SecurityUserStatusCode.INACTIVE,
                SecurityUserStatusCode.LOCKED,
                SecurityUserStatusCode.PASSWORD_EXPIRED
        };
    }

    @Override
    protected Mono<ULong> getLoggedInUserId() {
        return SecurityContextUtil.getUsersContextUser().map(ContextUser::getId).map(ULong::valueOf);
    }

    @Override
    public SecuritySoxLogObjectName getSoxObjectName() {
        return SecuritySoxLogObjectName.USER;
    }

    @PreAuthorize("hasAuthority('Authorities.User_CREATE')")
    @Override
    public Mono<User> create(User entity) {

        return FlatMapUtil.flatMapMono(
                        SecurityContextUtil::getUsersContextAuthentication,
                        ca -> {
                            if (entity.getClientId() == null) {
                                entity.setClientId(ULong.valueOf(ca.getUser().getClientId()));
                                return Mono.just(entity);
                            }

                            updateUserIdentificationKeys(entity);

                            if (ContextAuthentication.CLIENT_TYPE_SYSTEM.equals(ca.getClientTypeCode()))
                                return Mono.just(entity);

                            return Mono.zip(
                                            clientService.isBeingManagedBy(
                                                    ULong.valueOf(ca.getUser().getClientId()), entity.getClientId()),
                                            this.designationService.canAssignDesignation(
                                                    entity.getClientId(), entity.getDesignationId()),
                                            this.userSubOrgService.canReportTo(
                                                    entity.getClientId(), entity.getReportingTo(), null))
                                    .flatMap(e -> {
                                        if (!BooleanUtil.safeValueOf(e.getT2()))
                                            return this.securityMessageResourceService.throwMessage(
                                                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                                                    SecurityMessageResourceService.USER_DESIGNATION_MISMATCH);

                                        if (!BooleanUtil.safeValueOf(e.getT3()))
                                            return this.securityMessageResourceService.throwMessage(
                                                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                                                    SecurityMessageResourceService.USER_REPORTING_ERROR);

                                        return BooleanUtil.safeValueOf(e.getT1()) ? Mono.just(entity) : Mono.empty();
                                    });
                        },
                        (ca, user) -> checkUserIdentificationKeys(entity),
                        (ca, user, isValid) -> this.getPasswordEntities(user),
                        (ca, user, isValid, pass) -> this.passwordEntitiesPolicyCheck(
                                ULongUtil.valueOf(ca.getLoggedInFromClientId()),
                                ca.getUrlAppCode(),
                                user.getId(),
                                pass),
                        (ca, user, isValid, pass, passValid) -> this.checkBusinessClientUser(
                                user.getClientId(), user.getUserName(), user.getEmailId(), user.getPhoneNumber()),
                        (ca, user, isValid, pass, passValid, isAvailable) -> this.dao.create(user),
                        (ca, user, isValid, pass, passValid, isAvailable, createdUser) -> {
                            this.soxLogService.createLog(
                                    createdUser.getId(), CREATE, getSoxObjectName(), "User created");
                            return this.setPasswordEntities(createdUser, pass);
                        },
                        (ca, user, isValid, pass, passValid, isAvailable, createdUser, passSet) ->
                                this.evictOwnerCache(passSet.getClientId(), passSet.getId()).map(evicted -> passSet))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "UserService.create"))
                .switchIfEmpty(this.forbiddenError(SecurityMessageResourceService.FORBIDDEN_CREATE, "User"));
    }

    private void updateUserIdentificationKeys(User entity) {

        if (StringUtil.safeIsBlank(entity.getUserName())) entity.setUserName(User.PLACEHOLDER);

        if (StringUtil.safeIsBlank(entity.getEmailId())) entity.setEmailId(User.PLACEHOLDER);

        if (StringUtil.safeIsBlank(entity.getPhoneNumber())) entity.setPhoneNumber(User.PLACEHOLDER);
    }

    private Mono<Boolean> checkUserIdentificationKeys(User entity) {
        if (entity.checkIdentificationKeys())
            return this.forbiddenError(SecurityMessageResourceService.USER_IDENTIFICATION_NOT_FOUND);

        return Mono.just(Boolean.TRUE);
    }

    private Mono<Map<AuthenticationPasswordType, String>> getPasswordEntities(User user) {

        Map<AuthenticationPasswordType, String> passEntities = new EnumMap<>(AuthenticationPasswordType.class);

        if (!StringUtil.safeIsBlank(user.getPassword())) {
            passEntities.put(AuthenticationPasswordType.PASSWORD, user.getPassword());
            user.setPassword(null);
            user.setPasswordHashed(false);
        }

        if (!StringUtil.safeIsBlank(user.getPin())) {
            passEntities.put(AuthenticationPasswordType.PIN, user.getPin());
            user.setPin(null);
            user.setPinHashed(false);
        }

        return Mono.just(passEntities);
    }

    private Mono<Boolean> passwordEntitiesPolicyCheck(
            ULong clientId, String urlAppCode, ULong userId, Map<AuthenticationPasswordType, String> passEntities) {

        return Flux.fromIterable(passEntities.entrySet())
                .flatMap(passEntry -> this.passwordPolicyCheck(
                                clientId, null, urlAppCode, userId, passEntry.getKey(), passEntry.getValue())
                        .onErrorResume(e -> Mono.just(Boolean.FALSE)))
                .all(result -> result)
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "UserService.passwordEntitiesPolicyCheck"));
    }

    private Mono<Boolean> passwordPolicyCheck(
            ULong urlClientId,
            ULong appId,
            String appCode,
            ULong userId,
            AuthenticationPasswordType passwordType,
            String password) {

        return appId != null
                ? this.clientService.validatePasswordPolicy(urlClientId, appId, userId, passwordType, password)
                : this.clientService.validatePasswordPolicy(urlClientId, appCode, userId, passwordType, password);
    }

    private Mono<Boolean> checkBusinessClientUser(ULong clientId, String userName, String emailId, String phoneNumber) {

        return FlatMapUtil.flatMapMono(
                () -> this.clientService.getClientTypeNCode(clientId),
                clientTypeNCode ->
                        clientTypeNCode.getT1().equals("INDV") ? Mono.empty() : Mono.just(clientTypeNCode.getT1()),
                (clientTypeNCode, clientType) ->
                        this.dao.checkUserExists(clientId, "BUS", userName, emailId, phoneNumber));
    }

    private Mono<User> setPasswordEntities(User user, Map<AuthenticationPasswordType, String> passEntities) {

        return FlatMapUtil.flatMapMono(
                        this::getLoggedInUserId, loggedInUserId -> Flux.fromIterable(passEntities.entrySet())
                                .flatMap(passEntry -> this.setPassword(
                                        user.getId(), loggedInUserId, passEntry.getValue(), passEntry.getKey()))
                                .then(Mono.just(user)))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "UserService.setPasswordEntities"));
    }

    @Override
    public Mono<User> read(ULong id) {

        return super.read(id)
                .flatMap(
                        e -> SecurityContextUtil.getUsersContextAuthentication().flatMap(ca -> {
                            if (id.equals(ULong.valueOf(ca.getUser().getId()))) return Mono.just(e);

                            if (!SecurityContextUtil.hasAuthority("Authorities.User_READ", ca.getAuthorities()))
                                return Mono.defer(() -> this.forbiddenError(
                                        SecurityMessageResourceService.FORBIDDEN_PERMISSION, "User READ"));

                            return Mono.just(e);
                        }))
                .switchIfEmpty(
                        Mono.defer(() -> this.forbiddenError(AbstractMessageService.OBJECT_NOT_FOUND, "User", id)));
    }

    public Mono<UserResponse> readById(ULong userId) {
        return this.cacheService
                .cacheValueOrGet(CACHE_NAME_USER, () -> this.dao.readInternal(userId), userId)
                .flatMap(this::toUserResponse);
    }

    public Mono<List<UserResponse>> readByIds(List<ULong> userIds) {
        return this.readAllFilter(new FilterCondition()
                        .setField("id")
                        .setOperator(FilterConditionOperator.IN)
                        .setMultiValue(userIds))
                .flatMap(this::toUserResponse)
                .collectList();
    }

    private Mono<UserResponse> toUserResponse(User user) {
        return Mono.just(new UserResponse()
                .setId(user.getId().toBigInteger())
                .setClientId(user.getClientId().toBigInteger())
                .setUserName(user.getUserName())
                .setEmailId(user.getEmailId())
                .setPhoneNumber(user.getPhoneNumber())
                .setFirstName(user.getFirstName())
                .setLastName(user.getLastName())
                .setMiddleName(user.getMiddleName())
                .setLocaleCode(user.getLocaleCode()));
    }

    @PreAuthorize("hasAuthority('Authorities.User_READ')")
    @Override
    public Mono<Page<User>> readPageFilter(Pageable pageable, AbstractCondition condition) {
        return super.readPageFilter(pageable, condition);
    }

    @Override
    public Mono<User> update(ULong key, Map<String, Object> fields) {

        String userName =
                fields.containsKey("userName") ? fields.get("userName").toString() : null;

        String emailId = fields.containsKey("emailId") ? fields.get("emailId").toString() : null;

        String phoneNumber =
                fields.containsKey("phoneNumber") ? fields.get("phoneNumber").toString() : null;

        return FlatMapUtil.flatMapMono(
                        () -> this.dao.getUserClientId(key),
                        clientId ->
                                this.clientService.getClientTypeNCode(clientId).map(Tuple2::getT1),
                        (clientId, clientType) -> switch (clientType) {
                            case "INDV" -> this.clientHierarchyService
                                    .getManagingClient(clientId, ClientHierarchy.Level.ZERO)
                                    .flatMap(managingClientId -> this.dao.checkUserExistsExclude(
                                            managingClientId, userName, emailId, phoneNumber, "INDV", key));
                            case "BUS" -> this.dao.checkUserExists(clientId, userName, emailId, phoneNumber, null);
                            default -> Mono.empty();
                        },
                        (clientId, clientType, userExists) ->
                                Boolean.TRUE.equals(userExists) ? Mono.empty() : super.update(key, fields),
                        (clientId, clientType, userExists, updated) -> this.evictTokensAndOwnerCache(
                                        updated.getId(), updated.getClientId())
                                .<User>map(evicted -> updated))
                .switchIfEmpty(this.forbiddenError(SecurityMessageResourceService.FORBIDDEN_UPDATE, "user"));
    }

    @Override
    public Mono<User> update(User entity) {

        return FlatMapUtil.flatMapMono(
                        () -> this.clientService
                                .getClientTypeNCode(entity.getClientId())
                                .map(Tuple2::getT1),
                        clientType -> switch (clientType) {
                            case "INDV" -> this.clientHierarchyService
                                    .getManagingClient(entity.getClientId(), ClientHierarchy.Level.ZERO)
                                    .flatMap(managingClientId -> this.dao.checkUserExistsExclude(
                                            managingClientId,
                                            entity.getUserName(),
                                            entity.getEmailId(),
                                            entity.getPhoneNumber(),
                                            "INDV",
                                            entity.getId()));
                            case "BUS" -> this.dao.checkUserExists(
                                    entity.getClientId(),
                                    entity.getUserName(),
                                    entity.getEmailId(),
                                    entity.getPhoneNumber(),
                                    null);
                            default -> Mono.empty();
                        },
                        (clientType, userExists) ->
                                Boolean.TRUE.equals(userExists) ? Mono.empty() : super.update(entity),
                        (clientType, userExists, updated) -> this.evictTokensAndOwnerCache(
                                        updated.getId(), updated.getClientId())
                                .map(evicted -> updated))
                .switchIfEmpty(this.forbiddenError(SecurityMessageResourceService.FORBIDDEN_UPDATE, "user"));
    }

    private Mono<Integer> evictTokensAndOwnerCache(ULong userId, ULong clientId) {
        return Mono.zip(
                this.evictTokens(userId),
                this.evictOwnerCache(clientId, userId),
                (tEvicted, oEvicted) -> tEvicted == 1 && oEvicted == 1 ? 1 : 0);
    }

    private Mono<Integer> evictTokens(ULong id) {
        return this.tokenService.evictTokensOfUser(id);
    }

    private Mono<Integer> evictOwnerCache(ULong clientId, ULong userId) {
        return this.userSubOrgService.evictOwnerCache(clientId, userId).map(evicted -> Boolean.TRUE.equals(evicted) ? 1 : 0);
    }

    @Override
    protected Mono<User> updatableEntity(User entity) {
        return this.read(entity.getId()).map(e -> {
            e.setUserName(entity.getUserName());
            e.setEmailId(entity.getEmailId());
            e.setPhoneNumber(entity.getPhoneNumber());
            e.setFirstName(entity.getFirstName());
            e.setLastName(entity.getLastName());
            e.setMiddleName(entity.getMiddleName());
            e.setLocaleCode(entity.getLocaleCode());
            e.setStatusCode(entity.getStatusCode());
            // Note: reportingTo is not updated here as it requires a separate API call
            // Note: designationId is not updated here as it requires a separate API call
            return e;
        });
    }

    @PreAuthorize("hasAuthority('Authorities.User_DELETE')")
    @Override
    public Mono<Integer> delete(ULong id) {
        return this.read(id)
                .map(e -> {
                    e.setStatusCode(SecurityUserStatusCode.DELETED);
                    return e;
                })
                .flatMap(this::update)
                .flatMap(e -> this.evictTokensAndOwnerCache(e.getId(), e.getClientId())
                        .map(x -> 1));
    }

    public Mono<User> readInternal(String appCode, ULong id) {
        return this.dao.readInternal(id);
    }

    @PreAuthorize("hasAuthority('Authorities.User_UPDATE') and hasAuthority('Authorities.Role_READ')")
    public Mono<Boolean> removeRoleFromUser(ULong userId, ULong roleId) {
        return FlatMapUtil.flatMapMono(
                        SecurityContextUtil::getUsersContextAuthentication,
                        ca -> this.dao.readById(userId),
                        (ca, user) -> ca.isSystemClient()
                                ? Mono.just(true)
                                : clientService
                                .isBeingManagedBy(
                                        ULong.valueOf(ca.getUser().getClientId()), user.getClientId())
                                .flatMap(BooleanUtil::safeValueOfWithEmpty),
                        (ca, user, isManaged) -> this.dao
                                .removeRoleForUser(userId, roleId)
                                .map(val -> {
                                    boolean removed = val > 0;
                                    if (removed) super.unAssignLog(userId, UNASSIGNED_ROLE);

                                    return removed;
                                }),
                        (ca, user, isManaged, removed) -> this.evictTokensAndOwnerCache(userId, user.getClientId())
                                .map(evicted -> removed))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "UserService.removeRoleFromUser"))
                .flatMap(this.cacheService.evictFunction(CACHE_NAME_USER_ROLE, userId))
                .switchIfEmpty(this.forbiddenError(SecurityMessageResourceService.ROLE_REMOVE_ERROR, roleId, userId));
    }

    @PreAuthorize("hasAuthority('Authorities.User_UPDATE') and hasAuthority('Authorities.Role_READ')")
    public Mono<Boolean> assignRoleToUser(ULong userId, ULong roleId) {

        return this.dao.checkRoleAssignedForUser(userId, roleId).flatMap(result -> {
            if (Boolean.TRUE.equals(result)) return Mono.just(result);

            return FlatMapUtil.flatMapMono(
                            SecurityContextUtil::getUsersContextAuthentication,
                            ca -> this.dao.readById(userId),
                            (ca, user) -> ca.isSystemClient()
                                    ? Mono.just(true)
                                    : clientService
                                    .isBeingManagedBy(
                                            ULongUtil.valueOf(
                                                    ca.getUser().getClientId()),
                                            user.getClientId())
                                    .flatMap(BooleanUtil::safeValueOfWithEmpty),
                            (ca, user, sysOrManaged) -> this.profileService
                                    .hasAccessToRoles(user.getClientId(), Set.of(roleId))
                                    .flatMap(BooleanUtil::safeValueOfWithEmpty),
                            (ca, user, sysOrManaged, roleApplicable) -> this.dao
                                    .addRoleToUser(userId, roleId)
                                    .map(e -> {
                                        if (Boolean.TRUE.equals(e)) super.assignLog(userId, ASSIGNED_ROLE + roleId);

                                        return e;
                                    }),
                            (ca, user, sysOrManaged, roleApplicable, roleAssigned) -> this.evictTokensAndOwnerCache(
                                            userId, user.getClientId())
                                    .map(evicted -> roleAssigned))
                    .contextWrite(Context.of(LogUtil.METHOD_NAME, "UserService.assignRoleToUser"))
                    .flatMap(this.cacheService.evictFunction(CACHE_NAME_USER_ROLE, userId))
                    .switchIfEmpty(this.forbiddenError(SecurityMessageResourceService.ROLE_FORBIDDEN, roleId, userId));
        });
    }

    @PreAuthorize("hasAuthority('Authorities.User_UPDATE') and hasAuthority('Authorities.Profile_READ')")
    public Mono<Boolean> assignProfileToUser(ULong userId, ULong profileId) {

        return FlatMapUtil.flatMapMono(
                        SecurityContextUtil::getUsersContextAuthentication,
                        ca -> this.dao.readById(userId),
                        (ca, user) -> clientService
                                .isBeingManagedBy(ULongUtil.valueOf(ca.getUser().getClientId()), user.getClientId())
                                .filter(BooleanUtil::safeValueOf),
                        (ca, user, sysManaged) -> profileService
                                .hasAccessToProfiles(user.getClientId(), Set.of(profileId))
                                .filter(BooleanUtil::safeValueOf),
                        (ca, user, sysManaged, profileAccess) ->
                                this.dao.addProfileToUser(userId, profileId).map(e -> e != 0),
                        (ca, user, sysManaged, profileAccess, profileAssigned) -> this.evictTokensAndOwnerCache(
                                        userId, user.getClientId())
                                .map(evicted -> profileAssigned))
                .contextWrite(Context.of(
                        LogUtil.METHOD_NAME, "UserService.assignProfileToUser : [ " + userId + ", " + profileId + "]"))
                .switchIfEmpty(
                        this.forbiddenError(SecurityMessageResourceService.PROFILE_FORBIDDEN, profileId, userId));
    }

    @PreAuthorize("hasAuthority('Authorities.User_UPDATE') and hasAuthority('Authorities.Profile_READ')")
    public Mono<Boolean> removeProfileFromUser(ULong userId, ULong profileId) {

        return FlatMapUtil.flatMapMono(
                        SecurityContextUtil::getUsersContextAuthentication,
                        ca -> this.dao.readById(userId),
                        (ca, user) -> clientService
                                .isBeingManagedBy(ULongUtil.valueOf(ca.getUser().getClientId()), user.getClientId())
                                .filter(BooleanUtil::safeValueOf),
                        (ca, user, sysManaged) ->
                                this.dao.removeProfileForUser(userId, profileId).map(e -> e != 0),
                        (ca, user, sysManaged, profileRemoved) -> this.evictTokensAndOwnerCache(
                                        userId, user.getClientId())
                                .map(evicted -> profileRemoved))
                .contextWrite(Context.of(
                        LogUtil.METHOD_NAME, "UserService.assignProfileToUser : [ " + userId + ", " + profileId + "]"))
                .switchIfEmpty(
                        this.forbiddenError(SecurityMessageResourceService.PROFILE_FORBIDDEN, profileId, userId));
    }

    public Mono<Boolean> updatePassword(ULong userId, RequestUpdatePassword reqPassword) {

        return FlatMapUtil.flatMapMono(
                        SecurityContextUtil::getUsersContextAuthentication,
                        ca -> ca.isAuthenticated()
                                ? Mono.just(Boolean.TRUE)
                                : this.forbiddenError(SecurityMessageResourceService.LOGIN_REQUIRED),
                        (ca, loggedIn) -> updatePassword(ca, userId, reqPassword))
                .contextWrite(Context.of(
                        LogUtil.METHOD_NAME,
                        "UserService.updateNewPassword : [ " + userId + ", " + reqPassword.getPassType() + "]"))
                .switchIfEmpty(
                        this.forbiddenError(SecurityMessageResourceService.OBJECT_NOT_FOUND_TO_UPDATE, "User", userId));
    }

    public Mono<Boolean> updatePassword(RequestUpdatePassword reqPassword) {

        return FlatMapUtil.flatMapMono(
                        SecurityContextUtil::getUsersContextAuthentication,
                        ca -> ca.isAuthenticated()
                                ? Mono.just(Boolean.TRUE)
                                : this.forbiddenError(SecurityMessageResourceService.LOGIN_REQUIRED),
                        (ca, loggedIn) -> updatePassword(
                                ca, ULongUtil.valueOf(ca.getUser().getId()), reqPassword))
                .contextWrite(Context.of(
                        LogUtil.METHOD_NAME,
                        "UserService.updateNewPassword : [ loggedInUser, " + reqPassword.getPassType() + "]"))
                .switchIfEmpty(this.forbiddenError(SecurityMessageResourceService.OBJECT_NOT_UPDATABLE));
    }

    private Mono<Boolean> updatePassword(ContextAuthentication ca, ULong userId, RequestUpdatePassword reqPassword) {

        return FlatMapUtil.flatMapMono(
                        () -> this.dao.readInternal(userId),
                        user -> this.isPasswordUpdatable(ca, user, reqPassword, Boolean.TRUE),
                        (user, isUpdatable) -> this.checkHierarchy(ca, user),
                        (user, isUpdatable, inHierarchy) -> this.updatePasswordInternal(
                                ca,
                                user,
                                ULongUtil.valueOf(ca.getUser().getId()),
                                reqPassword.getPassType(),
                                reqPassword.getNewPassword(),
                                Boolean.FALSE))
                .contextWrite(Context.of(
                        LogUtil.METHOD_NAME, "UserService.updateNewPassword : [" + reqPassword.getPassType() + "]"))
                .switchIfEmpty(Mono.just(Boolean.FALSE))
                .log();
    }

    private Mono<Boolean> checkHierarchy(ContextAuthentication ca, User user) {

        ULong loggedInUserClientId = ULong.valueOf(ca.getUser().getClientId());

        if (ca.isSystemClient() || user.getClientId().equals(loggedInUserClientId)) return Mono.just(Boolean.TRUE);

        return Mono.zip(
                        SecurityContextUtil.hasAuthority("Authorities.User_UPDATE"),
                        this.clientService.isBeingManagedBy(loggedInUserClientId, user.getClientId()),
                        (hasAuthority, isManaged) -> hasAuthority && isManaged)
                .flatMap(BooleanUtil::safeValueOfWithEmpty)
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "UserService.checkHierarchy"))
                .switchIfEmpty(this.forbiddenError(SecurityMessageResourceService.HIERARCHY_ERROR))
                .log();
    }

    public Mono<Boolean> generateOtpResetPassword(AuthenticationRequest authRequest, ServerHttpRequest request) {

        OtpPurpose purpose = OtpPurpose.PASSWORD_RESET;

        return FlatMapUtil.flatMapMono(
                        SecurityContextUtil::getUsersContextAuthentication,
                        ca -> {
                            if (ca.isAuthenticated())
                                return this.forbiddenError(SecurityMessageResourceService.PASS_RESET_REQ_ERROR);

                            return this.findNonDeletedUserNClient(
                                    authRequest.getUserName(),
                                    authRequest.getUserId(),
                                    ca.getUrlClientCode(),
                                    null,
                                    authRequest.getComputedIdentifierType());
                        },
                        (ca, userTup) -> this.checkUserAndClient(userTup, ca.getUrlClientCode())
                                .flatMap(BooleanUtil::safeValueOfWithEmpty),
                        (ca, userTup, userCheck) -> this.appService.getAppByCode(ca.getUrlAppCode()),
                        (ca, userTup, userCheck, app) -> Mono.just(new OtpGenerationRequestInternal()
                                .setClientOption(userTup.getT1())
                                .setAppOption(app)
                                .setWithUserOption(userTup.getT3())
                                .setIpAddress(request.getRemoteAddress())
                                .setResend(authRequest.isResend())
                                .setPurpose(purpose)),
                        (ca, userTup, userCheck, app, targetReq) -> this.otpService.generateOtpInternal(targetReq))
                .contextWrite(Context.of(
                        LogUtil.METHOD_NAME,
                        "UserService.generateOtpResetPassword : [" + authRequest.getInputPassType() + "]"))
                .switchIfEmpty(Mono.just(Boolean.FALSE))
                .log();
    }

    public Mono<Boolean> verifyOtpResetPassword(AuthenticationRequest authRequest) {

        OtpVerificationRequest otpVerificationRequest = new OtpVerificationRequest()
                .setPurpose(OtpPurpose.PASSWORD_RESET)
                .setOtp(authRequest.getOtp());

        return FlatMapUtil.flatMapMono(
                        SecurityContextUtil::getUsersContextAuthentication,
                        ca -> {
                            if (ca.isAuthenticated())
                                return this.forbiddenError(SecurityMessageResourceService.PASS_RESET_REQ_ERROR);

                            return this.findNonDeletedUserNClient(
                                    authRequest.getUserName(),
                                    authRequest.getUserId(),
                                    ca.getUrlClientCode(),
                                    null,
                                    authRequest.getComputedIdentifierType());
                        },
                        (ca, userTup) -> this.checkUserAndClient(userTup, ca.getUrlClientCode())
                                .flatMap(BooleanUtil::safeValueOfWithEmpty),
                        (ca, userTup, userCheck) -> this.otpService
                                .verifyOtpInternal(ca.getUrlAppCode(), userTup.getT3(), otpVerificationRequest)
                                .filter(otpVerified -> otpVerified)
                                .map(otpVerified -> Boolean.TRUE)
                                .switchIfEmpty(this.forbiddenError(
                                        SecurityMessageResourceService.USER_PASSWORD_INVALID,
                                        AuthenticationPasswordType.OTP.getName(),
                                        AuthenticationPasswordType.OTP.getName())))
                .contextWrite(Context.of(
                        LogUtil.METHOD_NAME,
                        "UserService.verifyOtpResetPassword  : [" + authRequest.getInputPassType() + "]"))
                .switchIfEmpty(Mono.just(Boolean.FALSE))
                .log();
    }

    public Mono<Boolean> resetPassword(RequestUpdatePassword reqPassword) {

        AuthenticationRequest authRequest = reqPassword.getAuthRequest();

        OtpVerificationRequest otpVerificationRequest = new OtpVerificationRequest()
                .setPurpose(OtpPurpose.PASSWORD_RESET)
                .setOtp(authRequest.getOtp());

        return FlatMapUtil.flatMapMono(
                        SecurityContextUtil::getUsersContextAuthentication,
                        ca -> {
                            if (ca.isAuthenticated())
                                return this.forbiddenError(SecurityMessageResourceService.PASS_RESET_REQ_ERROR);

                            return this.findNonDeletedUserNClient(
                                    authRequest.getUserName(),
                                    authRequest.getUserId(),
                                    ca.getUrlClientCode(),
                                    null,
                                    authRequest.getComputedIdentifierType());
                        },
                        (ca, userTup) -> Mono.zip(
                                        this.checkUserAndClient(userTup, ca.getUrlClientCode()),
                                        this.checkUserStatus(userTup.getT3(), SecurityUserStatusCode.ACTIVE),
                                        (userClientCheck, userStatusCheck) -> userClientCheck && userStatusCheck)
                                .flatMap(BooleanUtil::safeValueOfWithEmpty)
                                .switchIfEmpty(this.forbiddenError(SecurityMessageResourceService.USER_NOT_ACTIVE)),
                        (ca, userTup, userCheck) ->
                                this.isPasswordUpdatable(ca, userTup.getT3(), reqPassword, Boolean.FALSE),
                        (ca, userTup, userCheck, isUpdatable) -> this.otpService
                                .verifyOtpInternal(ca.getUrlAppCode(), userTup.getT3(), otpVerificationRequest)
                                .flatMap(BooleanUtil::safeValueOfWithEmpty)
                                .switchIfEmpty(this.forbiddenError(
                                        SecurityMessageResourceService.USER_PASSWORD_INVALID,
                                        AuthenticationPasswordType.OTP.getName(),
                                        AuthenticationPasswordType.OTP.getName())),
                        (ca, userTup, userCheck, isUpdatable, otpVerified) -> this.updatePasswordInternal(
                                ca,
                                userTup.getT3(),
                                userTup.getT3().getId(),
                                reqPassword.getPassType(),
                                reqPassword.getNewPassword(),
                                Boolean.TRUE))
                .contextWrite(Context.of(
                        LogUtil.METHOD_NAME, "UserService.resetPassword  : [" + authRequest.getInputPassType() + "]"))
                .switchIfEmpty(Mono.just(Boolean.FALSE))
                .log();
    }

    private Mono<Boolean> isPasswordUpdatable(
            ContextAuthentication ca, User user, RequestUpdatePassword reqPassword, boolean isUpdate) {

        if (StringUtil.safeIsBlank(reqPassword.getNewPassword()))
            return this.forbiddenError(SecurityMessageResourceService.NEW_PASSWORD_MISSING);

        boolean isSameUser = user.getId().equals(ULongUtil.valueOf(ca.getUser().getId()));

        return FlatMapUtil.flatMapMono(
                        () -> (isUpdate && isSameUser
                                ? this.checkPasswordEquality(user, reqPassword)
                                : Mono.just(Boolean.TRUE)),
                        areEqual -> this.passwordPolicyCheck(
                                ULongUtil.valueOf(ca.getLoggedInFromClientId()),
                                null,
                                ca.getUrlAppCode(),
                                user.getId(),
                                reqPassword.getPassType(),
                                reqPassword.getNewPassword()))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "UserService.isPasswordUpdatable"));
    }

    private Mono<Boolean> checkPasswordEquality(User user, RequestUpdatePassword reqPassword) {

        return FlatMapUtil.flatMapMono(
                        () -> this.checkOldPassword(user, reqPassword)
                                .flatMap(BooleanUtil::safeValueOfWithEmpty)
                                .switchIfEmpty(this.forbiddenError(SecurityMessageResourceService.OLD_PASSWORD_MATCH)),
                        oldCheck -> this.checkNewPassword(user, reqPassword)
                                .flatMap(BooleanUtil::safeValueOfWithEmpty)
                                .switchIfEmpty(this.forbiddenError(SecurityMessageResourceService.NEW_PASSWORD_MATCH)),
                        (oldCheck, newCheck) -> Mono.just(oldCheck && newCheck))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "UserService.checkPasswordEquality"));
    }

    private Mono<Boolean> checkNewPassword(User user, RequestUpdatePassword reqPassword) {
        return this.checkPassword(user, reqPassword.getNewPassword(), reqPassword.getPassType(), false);
    }

    private Mono<Boolean> checkOldPassword(User user, RequestUpdatePassword reqPassword) {
        return this.checkPassword(user, reqPassword.getOldPassword(), reqPassword.getPassType(), true);
    }

    private Mono<Boolean> checkPassword(
            User user, String password, AuthenticationPasswordType passType, boolean matchExpected) {

        boolean matches =
                switch (passType) {
                    case PASSWORD -> user.isPasswordHashed()
                            ? passwordEncoder.matches(user.getId() + password, user.getPassword())
                            : StringUtil.safeEquals(password, user.getPassword());
                    case PIN -> user.isPinHashed()
                            ? passwordEncoder.matches(user.getId() + password, user.getPin())
                            : StringUtil.safeEquals(password, user.getPin());
                    default -> false;
                };

        return Mono.just(matchExpected == matches);
    }

    private Mono<Boolean> updatePasswordInternal(
            ContextAuthentication ca,
            User user,
            ULong currentUserId,
            AuthenticationPasswordType passType,
            String newPassword,
            boolean isReset) {

        return FlatMapUtil.flatMapMono(
                        () -> this.setPassword(user.getId(), currentUserId, newPassword, passType), passSet -> {
                            this.soxLogService.createLog(
                                    user.getId(),
                                    SecuritySoxLogActionName.OTHER,
                                    SecuritySoxLogObjectName.USER,
                                    StringFormatter.format("$ updated", passType));

                            return ecService.createEvent(new EventQueObject()
                                    .setAppCode(ca.getUrlAppCode())
                                    .setClientCode(ca.getUrlClientCode())
                                    .setEventName(
                                            isReset
                                                    ? EventNames.getEventName(
                                                    EventNames.USER_PASSWORD_RESET_DONE, passType)
                                                    : EventNames.getEventName(
                                                    EventNames.USER_PASSWORD_CHANGED, passType))
                                    .setData(Map.of("user", user)));
                        })
                .flatMap(e -> this.evictTokens(user.getId()).map(x -> e))
                .flatMap(e -> this.unlockUserInternal(user.getId()).map(x -> e))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "UserService.updateNewPassword"))
                .switchIfEmpty(this.forbiddenError("$ cannot be updated", passType));
    }

    private Mono<Boolean> setPassword(
            ULong userId, ULong currentUserId, String password, AuthenticationPasswordType passwordType) {

        if (currentUserId == null) currentUserId = userId;

        return this.dao
                .setPassword(userId, currentUserId, password, passwordType)
                .map(result -> result > 0)
                .flatMap(BooleanUtil::safeValueOfWithEmpty)
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "UserService.setPassword"));
    }

    public Mono<List<UserClient>> findUserClients(
            AuthenticationRequest authRequest, Boolean appLevel, ServerHttpRequest request) {

        String appCode = appLevel ? request.getHeaders().getFirst(AppService.AC) : null;

        String clientCode = request.getHeaders().getFirst(ClientService.CC);

        return this.findUserClients(authRequest, appCode, clientCode, this.getNonDeletedUserStatusCodes());
    }

    private Mono<List<UserClient>> findUserClients(
            AuthenticationRequest authRequest,
            String appCode,
            String clientCode,
            SecurityUserStatusCode... userStatusCodes) {

        return this.dao
                .getAllClientsBy(
                        authRequest.getUserName(),
                        authRequest.getUserId(),
                        clientCode,
                        appCode,
                        authRequest.getComputedIdentifierType(),
                        userStatusCodes)
                .flatMapMany(map -> Flux.fromIterable(map.entrySet()))
                .flatMap(e -> this.clientService.getClientInfoById(e.getValue()).map(c -> Tuples.of(e.getKey(), c)))
                .collectList()
                .map(e -> e.stream()
                        .map(x -> new UserClient(x.getT1(), x.getT2()))
                        .sorted()
                        .toList());
    }

    // Don't call this method other than from the client service register method
    public Mono<User> createForRegistration(
            ULong appId,
            ULong appClientId,
            ULong urlClientId,
            Client client,
            User user,
            AuthenticationPasswordType passwordType) {

        String password = user.getInputPass(passwordType);
        user.setPassword(null);
        user.setPasswordHashed(false);
        user.setAccountNonExpired(true);
        user.setAccountNonLocked(true);
        user.setCredentialsNonExpired(true);

        return FlatMapUtil.flatMapMono(
                        () -> this.dao
                                .checkUserExists(
                                        user.getClientId(),
                                        user.getUserName(),
                                        user.getEmailId(),
                                        user.getPhoneNumber(),
                                        "INDV")
                                .filter(userExists -> !userExists)
                                .map(userExists -> Boolean.FALSE),
                        userExists -> this.dao.create(user),
                        (userExists, createdUser) -> {
                            this.soxLogService.createLog(
                                    createdUser.getId(), CREATE, getSoxObjectName(), "User created");

                            return this.setPassword(createdUser.getId(), createdUser.getId(), password, passwordType);
                        },
                        (userExists, createdUser, passSet) -> {
                            Mono<Boolean> roleUser = FlatMapUtil.flatMapMono(
                                            SecurityContextUtil::getUsersContextAuthentication,
                                            ca -> this.addDefaultRoles(
                                                    appId, appClientId, urlClientId, client, createdUser.getId()),
                                            (ca, rolesAdded) -> this.addDefaultProfiles(
                                                    appId, appClientId, urlClientId, client, createdUser.getId()),
                                            (ca, rolesAdded, profilesAdded) -> this.addDesignation(
                                                    appId, appClientId, urlClientId, client, createdUser.getId()))
                                    .contextWrite(Context.of(LogUtil.METHOD_NAME, "UserService.createForRegistration"));

                            return roleUser.map(x -> createdUser);
                        })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "UserService.createForRegistration"))
                .switchIfEmpty(this.forbiddenError(SecurityMessageResourceService.FORBIDDEN_CREATE, "User"));
    }

    public Mono<Boolean> addDesignation(
            ULong appId, ULong appClientId, ULong urlClientId, Client client, ULong userId) {

        return FlatMapUtil.flatMapMono(
                        () -> this.clientService.getClientLevelType(client.getId(), appId),
                        levelType -> this.appRegistrationDAO.getDepartmentsForRegistration(
                                appId,
                                appClientId,
                                urlClientId,
                                client.getTypeCode(),
                                levelType,
                                client.getBusinessType()),
                        (levelType, departments) -> this.departmentService.createForRegistration(client, departments),
                        (levelType, departments, departmentIndex) ->
                                this.appRegistrationDAO.getDesignationsForRegistration(
                                        appId,
                                        appClientId,
                                        urlClientId,
                                        client.getTypeCode(),
                                        levelType,
                                        client.getBusinessType()),
                        (levelType, departments, departmentIndex, designations) ->
                                this.designationService.createForRegistration(client, designations, departmentIndex),
                        (levelType, departments, departmentIndex, designations, designationIndex) ->
                                this.appRegistrationDAO.getUserDesignationsForRegistration(
                                        appId,
                                        appClientId,
                                        urlClientId,
                                        client.getTypeCode(),
                                        levelType,
                                        client.getBusinessType()),
                        (levelType, departments, departmentIndex, designations, designationIndex, userDesignations) -> {
                            if (userDesignations.isEmpty()
                                    || !designationIndex.containsKey(
                                    userDesignations.get(0).getDesignationId())) return Mono.just(true);

                            return this.dao.addDesignation(
                                    userId,
                                    designationIndex
                                            .get(userDesignations.get(0).getDesignationId())
                                            .getT2()
                                            .getId());
                        })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "UserService.addDesignation"));
    }

    public Mono<Boolean> addDefaultRoles(
            ULong appId, ULong appClientId, ULong urlClientId, Client client, ULong userId) {

        return FlatMapUtil.flatMapMono(
                        () -> this.clientService.getClientLevelType(client.getId(), appId),
                        levelType -> this.appRegistrationDAO.getRoleIdsForUserRegistration(
                                appId,
                                appClientId,
                                urlClientId,
                                client.getTypeCode(),
                                levelType,
                                client.getBusinessType()),
                        (levelType, roles) -> Flux.fromIterable(roles)
                                .flatMap(roleId -> this.dao.addRoleToUser(userId, roleId))
                                .collectList()
                                .<Boolean>map(e -> true))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "UserService.addDefaultRoles"));
    }

    public Mono<Boolean> addDefaultProfiles(
            ULong appId, ULong appClientId, ULong urlClientId, Client client, ULong userId) {

        return FlatMapUtil.flatMapMono(
                        () -> this.clientService.getClientLevelType(client.getId(), appId),
                        levelType -> this.appRegistrationDAO.getProfileIdsForUserRegistration(
                                appId,
                                appClientId,
                                urlClientId,
                                client.getTypeCode(),
                                levelType,
                                client.getBusinessType()),
                        (levelType, profiles) -> Flux.fromIterable(profiles)
                                .flatMap(profileId -> this.dao.addProfileToUser(userId, profileId))
                                .collectList()
                                .<Boolean>map(e -> true))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "UserService.addDefaultRoles"));
    }

    @PreAuthorize("hasAuthority('Authorities.User_UPDATE')")
    public Mono<Boolean> makeUserActive(ULong userId) {

        return FlatMapUtil.flatMapMono(
                        SecurityContextUtil::getUsersContextAuthentication,
                        ca -> Mono.justOrEmpty(CommonsUtil.nonNullValue(
                                userId, ULong.valueOf(ca.getUser().getId()))),
                        (ca, id) -> ca.isSystemClient()
                                ? Mono.just(Boolean.TRUE)
                                : this.dao
                                .readById(id)
                                .flatMap(e -> this.clientService.isBeingManagedBy(
                                        ULong.valueOf(ca.getLoggedInFromClientId()), e.getClientId())),
                        (ca, id, sysOrManaged) -> Boolean.FALSE.equals(sysOrManaged)
                                ? Mono.empty()
                                : this.dao.makeUserActiveIfInActive(id))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "UserService.makeUserActive"))
                .switchIfEmpty(this.forbiddenError(SecurityMessageResourceService.ACTIVE_INACTIVE_ERROR, "user"));
    }

    @PreAuthorize("hasAuthority('Authorities.User_UPDATE')")
    public Mono<Boolean> makeUserInActive(ULong userId) {

        return FlatMapUtil.flatMapMono(
                        SecurityContextUtil::getUsersContextAuthentication,
                        ca -> Mono.justOrEmpty(CommonsUtil.nonNullValue(
                                userId, ULong.valueOf(ca.getUser().getId()))),
                        (ca, id) -> ca.isSystemClient()
                                ? Mono.just(Boolean.TRUE)
                                : this.dao
                                .readById(id)
                                .flatMap(e -> this.clientService.isBeingManagedBy(
                                        ULong.valueOf(ca.getLoggedInFromClientId()), e.getClientId())),
                        (ca, id, sysOrManaged) ->
                                Boolean.FALSE.equals(sysOrManaged) ? Mono.empty() : this.dao.makeUserInActive(id))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "UserService.makeUserInActive"))
                .switchIfEmpty(this.forbiddenError(SecurityMessageResourceService.ACTIVE_INACTIVE_ERROR, "user"));
    }

    @PreAuthorize("hasAuthority('Authorities.User_UPDATE')")
    public Mono<Boolean> unblockUser(ULong userId) {

        return FlatMapUtil.flatMapMono(
                        SecurityContextUtil::getUsersContextAuthentication,
                        ca -> Mono.justOrEmpty(CommonsUtil.nonNullValue(
                                userId, ULong.valueOf(ca.getUser().getId()))),
                        (ca, id) -> ca.isSystemClient()
                                ? Mono.just(Boolean.TRUE)
                                : this.dao
                                .readById(id)
                                .flatMap(e -> this.clientService.isBeingManagedBy(
                                        ULong.valueOf(ca.getLoggedInFromClientId()), e.getClientId())),
                        (ca, id, sysOrManaged) ->
                                Boolean.FALSE.equals(sysOrManaged) ? Mono.empty() : this.unlockUserInternal(id))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "UserService.unblockUser"))
                .switchIfEmpty(this.forbiddenError(SecurityMessageResourceService.ACTIVE_INACTIVE_ERROR, "user"));
    }

    public Mono<Boolean> lockUserInternal(ULong userId, LocalDateTime lockUntil, String lockedDueTo) {
        return this.dao.lockUser(userId, lockUntil, lockedDueTo);
    }

    public Mono<Boolean> unlockUserInternal(ULong userId) {
        return this.dao.updateUserStatusToActive(userId);
    }

    public Mono<Boolean> checkIndividualClientUser(String urlClientCode, ClientRegistrationRequest request) {
        return this.clientService
                .getClientId(urlClientCode)
                .flatMap(clientId -> this.dao.checkUserExists(
                        clientId, request.getUserName(), request.getEmailId(), request.getPhoneNumber(), "INDV"));
    }

    public Mono<TokenObject> makeOneTimeToken(
            ServerHttpRequest httpRequest, ContextAuthentication ca, User user, ULong loggedInClientId) {

        String host = httpRequest.getURI().getHost();

        String port = "" + httpRequest.getURI().getPort();

        List<String> forwardedHost = httpRequest.getHeaders().get("X-Forwarded-Host");

        if (forwardedHost != null && !forwardedHost.isEmpty()) {
            host = forwardedHost.getFirst();
        }

        List<String> forwardedPort = httpRequest.getHeaders().get("X-Forwarded-Port");

        if (forwardedPort != null && !forwardedPort.isEmpty()) {
            port = forwardedPort.getFirst();
        }

        InetSocketAddress inetAddress = httpRequest.getRemoteAddress();
        final String hostAddress = inetAddress == null ? null : inetAddress.getHostString();

        Tuple2<String, LocalDateTime> token = JWTUtil.generateToken(JWTGenerateTokenParameters.builder()
                .userId(user.getId().toBigInteger())
                .secretKey(tokenKey)
                .expiryInMin(VALIDITY_MINUTES)
                .host(host)
                .port(port)
                .loggedInClientId(loggedInClientId.toBigInteger())
                .loggedInClientCode(ca.getUrlClientCode())
                .oneTime(true)
                .build());

        return tokenService.create(new TokenObject()
                .setUserId(user.getId())
                .setToken(token.getT1())
                .setPartToken(
                        token.getT1().length() < 50
                                ? token.getT1()
                                : token.getT1().substring(token.getT1().length() - 50))
                .setExpiresAt(token.getT2())
                .setIpAddress(hostAddress));
    }

    public Mono<List<Profile>> assignedProfiles(ULong userId, ULong appId) {
        return this.profileService.assignedProfiles(userId, appId);
    }

    public Mono<Boolean> checkUserExistsAcrossApps(String userName, String email, String phoneNumber) {

        if (userName == null && email == null && phoneNumber == null)
            return this.securityMessageResourceService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    SecurityMessageResourceService.USER_DESIGNATION_MISMATCH);

        return FlatMapUtil.flatMapMono(
                SecurityContextUtil::getUsersContextAuthentication,
                ca -> this.dao.checkUserExists(
                        ULong.valueOf(ca.getLoggedInFromClientId()), userName, email, phoneNumber, null));
    }

    @PreAuthorize("hasAuthority('Authorities.User_UPDATE')")
    public Mono<User> updateDesignation(ULong userId, ULong designationId) {

        return FlatMapUtil.flatMapMono(
                        SecurityContextUtil::getUsersContextAuthentication,
                        ca -> this.dao.readById(userId),
                        (ca, user) -> ca.isSystemClient()
                                ? Mono.just(Boolean.TRUE)
                                : clientService
                                .isBeingManagedBy(
                                        ULongUtil.valueOf(ca.getUser().getClientId()), user.getClientId())
                                .flatMap(BooleanUtil::safeValueOfWithEmpty),
                        (ca, user, sysOrManaged) -> this.designationService
                                .canAssignDesignation(user.getClientId(), designationId)
                                .flatMap(canAssign -> !BooleanUtil.safeValueOf(canAssign)
                                        ? this.securityMessageResourceService.throwMessage(
                                        msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                                        SecurityMessageResourceService.USER_DESIGNATION_MISMATCH)
                                        : Mono.just(user)),
                        (ca, user, sysOrManaged, validUser) -> super.update(user.setDesignationId(designationId)),
                        (ca, user, sysOrManaged, validUser, updated) -> this.evictTokensAndOwnerCache(
                                        updated.getId(), updated.getClientId())
                                .map(evicted -> updated))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "UserService.updateDesignation"))
                .switchIfEmpty(
                        this.forbiddenError(SecurityMessageResourceService.FORBIDDEN_UPDATE, "user designation"));
    }

    public Mono<Boolean> checkIfUserIsOwner(ULong userId) {


        if (userId == null) return Mono.empty();

        return this.dao.checkIfUserIsOwner(userId);
    }

    public Mono<Map<String, Object>> getUserAdminEmails(ServerHttpRequest request) {

        String appCode = request.getHeaders().getFirst(AppService.AC);

        String clientCode = request.getHeaders().getFirst(ClientService.CC);

        List<String> authorities = List.of("Authorities.User_CREATE", "Authorities.ROLE_Owner");

        return FlatMapUtil.flatMapMono(

                        () -> this.appService.getAppByCode(appCode),

                        app -> this.clientService.getClientBy(clientCode),

                        (app, client) -> this.profileService.getAppProfilesHavingAuthorities(
                                app.getId(), client.getId(), authorities),

                        (app, client, appAdminProfiles) -> this.dao
                                .getUsersForProfiles(appAdminProfiles, client.getId())
                                .flatMap(users -> {
                                    if (users.isEmpty()) {
                                        return Mono.empty(); // Convert empty list to empty Mono to trigger switchIfEmpty
                                    }
                                    return Mono.just(users);
                                })
                                .map(users -> users.stream()
                                        .map(User::getEmailId)
                                        .filter(Objects::nonNull)
                                        .collect(Collectors.toList()))
                                .map(emails -> Map.of("emails", emails, "addApp", Boolean.FALSE))
                                .switchIfEmpty(this.dao
                                        .getOwners(client.getId())
                                        .map(users -> users.stream()
                                                .map(User::getEmailId)
                                                .filter(Objects::nonNull)
                                                .collect(Collectors.toList()))
                                        .map(emails -> Map.of("emails", emails, "addApp", Boolean.TRUE))))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "UserService.getAppUserAdminEmails"))
                .defaultIfEmpty(Map.of("emails", List.of(), "addApp", Boolean.FALSE));
    }
}
