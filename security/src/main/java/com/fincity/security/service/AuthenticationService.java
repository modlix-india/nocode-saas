package com.fincity.security.service;

import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import com.fincity.security.dto.OneTimeToken;
import com.fincity.security.model.*;
import org.jetbrains.annotations.Nullable;
import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.commons.model.condition.FilterConditionOperator;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.security.jwt.ContextUser;
import com.fincity.saas.commons.security.jwt.JWTClaims;
import com.fincity.saas.commons.security.jwt.JWTUtil;
import com.fincity.saas.commons.security.jwt.JWTUtil.JWTGenerateTokenParameters;
import com.fincity.saas.commons.security.service.IAuthenticationService;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.service.CacheService;
import com.fincity.saas.commons.util.BooleanUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.security.dao.AppRegistrationIntegrationTokenDao;
import com.fincity.security.dto.Client;
import com.fincity.security.dto.TokenObject;
import com.fincity.security.dto.User;
import com.fincity.security.dto.policy.AbstractPolicy;
import com.fincity.security.enums.otp.OtpPurpose;
import com.fincity.security.jooq.enums.SecuritySoxLogActionName;
import com.fincity.security.jooq.enums.SecuritySoxLogObjectName;
import com.fincity.security.model.otp.OtpGenerationRequestInternal;
import com.fincity.security.model.otp.OtpVerificationRequest;
import com.fincity.security.service.appregistration.AppRegistrationIntegrationTokenService;

import reactor.core.publisher.Mono;
import reactor.util.context.Context;
import reactor.util.function.Tuple2;

@Service
public class AuthenticationService implements IAuthenticationService {

    private final UserService userService;

    private final ClientService clientService;

    private final AppService appService;

    private final TokenService tokenService;

    private final OtpService otpService;

    private final SecurityMessageResourceService resourceService;

    private final SoxLogService soxLogService;

    private final PasswordEncoder pwdEncoder;

    private final CacheService cacheService;

    private final ProfileService profileService;

    private final AppRegistrationIntegrationTokenDao integrationTokenDao;

    private final AppRegistrationIntegrationTokenService appRegistrationIntegrationTokenService;

    private final OneTimeTokenService oneTimeTokenService;

    @Value("${security.appCodeSuffix:}")
    private String appCodeSuffix;

    public AuthenticationService(
            UserService userService,
            ClientService clientService,
            AppService appService,
            TokenService tokenService,
            OtpService otpService,
            SecurityMessageResourceService resourceService,
            SoxLogService soxLogService,
            PasswordEncoder pwdEncoder,
            CacheService cacheService,
            AppRegistrationIntegrationTokenDao integrationTokenDao,
            AppRegistrationIntegrationTokenService appRegistrationIntegrationTokenService,
            ProfileService profileService,
            OneTimeTokenService oneTimeTokenService) {
        this.userService = userService;
        this.clientService = clientService;
        this.appService = appService;
        this.tokenService = tokenService;
        this.otpService = otpService;
        this.resourceService = resourceService;
        this.soxLogService = soxLogService;
        this.pwdEncoder = pwdEncoder;
        this.cacheService = cacheService;
        this.integrationTokenDao = integrationTokenDao;
        this.appRegistrationIntegrationTokenService = appRegistrationIntegrationTokenService;
        this.profileService = profileService;
        this.oneTimeTokenService = oneTimeTokenService;
    }

    @Value("${jwt.key}")
    private String tokenKey;

    @Value("${jwt.token.rememberme.expiry}")
    private Integer rememberMeExpiryInMinutes;

    @Value("${jwt.token.default.expiry}")
    private Integer defaultExpiryInMinutes;

    @Value("${jwt.token.default.refresh:10}")
    private Integer defaultRefreshInMinutes;

    public Mono<Integer> revoke(ServerHttpRequest request) {

        String bearerToken = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        if (bearerToken == null || bearerToken.isBlank()) {
            HttpCookie cookie = request.getCookies().getFirst(HttpHeaders.AUTHORIZATION);
            if (cookie != null) bearerToken = cookie.getValue();
        }

        if (bearerToken != null) {

            bearerToken = bearerToken.trim();

            if (bearerToken.startsWith("Bearer ")) {
                bearerToken = bearerToken.substring(7);
            } else if (bearerToken.startsWith("Basic ")) {
                bearerToken = bearerToken.substring(6);
            }
        }

        if (bearerToken == null) return Mono.just(1);

        final String finToken = bearerToken;

        return cacheService.evict(CACHE_NAME_TOKEN, finToken)
                .flatMap(x -> tokenService
                        .readAllFilter(new FilterCondition()
                                .setField("partToken")
                                .setOperator(FilterConditionOperator.EQUALS)
                                .setValue(toPartToken(finToken)))
                        .filter(e -> e.getToken().equals(finToken))
                        .map(TokenObject::getId)
                        .collectList()
                        .flatMap(e -> e.isEmpty() ? Mono.empty() : Mono.just(e.getFirst()))
                        .flatMap(tokenService::delete)
                        .defaultIfEmpty(1));
    }

    public Mono<Boolean> generateOtp(AuthenticationRequest authRequest, ServerHttpRequest request) {

        if (!authRequest.isGenerateOtp()) return Mono.just(Boolean.FALSE);

        String appCode = request.getHeaders().getFirst(AppService.AC);
        String clientCode = request.getHeaders().getFirst(ClientService.CC);

        OtpPurpose purpose = OtpPurpose.LOGIN;

        if (authRequest.getIdentifierType() == null) authRequest.setIdentifierType();

        return FlatMapUtil.flatMapMono(
                        () -> this.userService.findNonDeletedUserNClient(
                                authRequest.getUserName(),
                                authRequest.getUserId(),
                                clientCode,
                                appCode,
                                authRequest.getIdentifierType()),
                        tup -> this.userService
                                .checkUserAndClient(tup, clientCode)
                                .flatMap(BooleanUtil::safeValueOfWithEmpty),
                        (tup, linCCheck) -> this.appService.getAppByCode(appCode),
                        (tup, linCCheck, app) -> Mono.just(new OtpGenerationRequestInternal()
                                .setClientOption(tup.getT1())
                                .setAppOption(app)
                                .setWithUserOption(tup.getT3())
                                .setIpAddress(request.getRemoteAddress())
                                .setResend(authRequest.isResend())
                                .setPurpose(purpose)),
                        (app, tup, linCCheck, targetReq) -> this.otpService.generateOtpInternal(targetReq))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "OtpService.generateOtp : [" + purpose.name() + "]"))
                .switchIfEmpty(Mono.just(Boolean.FALSE))
                .log();
    }

    public Mono<AuthenticationResponse> authenticate(
            AuthenticationRequest authRequest, ServerHttpRequest request, ServerHttpResponse response) {

        String appCode = request.getHeaders().getFirst(AppService.AC);
        String clientCode = request.getHeaders().getFirst(ClientService.CC);

        if (authRequest.getIdentifierType() == null) authRequest.setIdentifierType();

        AuthenticationPasswordType passwordType = authRequest.getInputPassType();

        if (passwordType == null) {
            return this.authError(SecurityMessageResourceService.UNKNOWN_ERROR);
        }

        return FlatMapUtil.flatMapMono(
                        () -> this.userService
                                .findNonDeletedUserNClient(
                                        authRequest.getUserName(),
                                        authRequest.getUserId(),
                                        clientCode,
                                        appCode,
                                        authRequest.getIdentifierType())
                                .flatMap(tup -> this.profileService
                                        .checkIfUserHasAnyProfile(tup.getT3().getId(), appCode)
                                        .flatMap(e -> {
                                            if (BooleanUtil.safeValueOf(e)) return Mono.just(tup);
                                            return Mono.empty();
                                        })),
                        tup -> this.userService
                                .checkUserAndClient(tup, clientCode)
                                .flatMap(BooleanUtil::safeValueOfWithEmpty),
                        (tup, linCCheck) -> this.checkUserStatus(tup.getT3()),
                        (tup, linCCheck, user) -> this.clientService.getClientAppPolicy(
                                tup.getT2().getId(), appCode, passwordType),
                        (tup, linCCheck, user, policy) ->
                                this.checkPassword(authRequest.getInputPass(), appCode, user, policy, passwordType),
                        (tup, linCCheck, user, policy, passwordChecked) -> this.resetUserAttempts(user, passwordType),
                        (tup, linCCheck, user, policy, passwordChecked, attemptsReset) ->
                                logAndMakeToken(authRequest.isRememberMe(), authRequest.isCookie(), request, response, user, tup.getT2(), tup.getT1()))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "AuthenticationService.authenticate"))
                .switchIfEmpty(this.authError(SecurityMessageResourceService.USER_CREDENTIALS_MISMATCHED));
    }

    public Mono<AuthenticationResponse> authenticateWSocial(
            AuthenticationRequest authRequest, ServerHttpRequest request, ServerHttpResponse response) {

        if (StringUtil.safeIsBlank(authRequest.getSocialRegisterState()))
            return this.resourceService.throwMessage(
                    msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                    SecurityMessageResourceService.SOCIAL_LOGIN_FAILED);

        String appCode = request.getHeaders().getFirst(AppService.AC);
        String clientCode = request.getHeaders().getFirst(ClientService.CC);

        if (authRequest.getIdentifierType() == null)
            authRequest.setIdentifierType(AuthenticationIdentifierType.EMAIL_ID);

        return FlatMapUtil.flatMapMono(
                        () -> this.userService
                                .findNonDeletedUserNClient(
                                        authRequest.getUserName(),
                                        authRequest.getUserId(),
                                        clientCode,
                                        appCode,
                                        authRequest.getIdentifierType())
                                .flatMap(tup -> this.profileService
                                        .checkIfUserHasAnyProfile(tup.getT3().getId(), appCode)
                                        .flatMap(e -> {
                                            if (BooleanUtil.safeValueOf(e)) return Mono.just(tup);
                                            return Mono.empty();
                                        })),
                        tup -> this.userService
                                .checkUserAndClient(tup, clientCode)
                                .flatMap(BooleanUtil::safeValueOfWithEmpty),
                        (tup, linCCheck) -> this.checkUserStatus(tup.getT3()),
                        (tup, linCCheck, user) -> this.appRegistrationIntegrationTokenService.verifyIntegrationState(
                                authRequest.getSocialRegisterState()),
                        (tup, linCCheck, user, appRegIntgToken) -> Mono.just(
                                        appRegIntgToken.getUsername().equals(authRequest.getUserName()))
                                .flatMap(BooleanUtil::safeValueOfWithEmpty),
                        (tup, linCCheck, user, appRegIntgToken, usernameChecked) -> {
                            appRegIntgToken.setCreatedBy(user.getId());
                            appRegIntgToken.setUpdatedBy(user.getId());

                            return this.integrationTokenDao.update(appRegIntgToken);
                        },
                        (tup, linCCheck, user, appRegIntgToken, usernameChecked, updatedToken) ->
                                logAndMakeToken(authRequest.isRememberMe(), authRequest.isCookie(), request, response, user, tup.getT2(), tup.getT1()))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "AuthenticationService.authenticateWSocial"))
                .switchIfEmpty(this.authError(SecurityMessageResourceService.USER_CREDENTIALS_MISMATCHED))
                .log();
    }

    private Mono<User> checkUserStatus(User user) {
        return switch (user.getStatusCode()) {
            case ACTIVE -> Mono.just(user);
            case LOCKED -> this.checkUserLockStatus(user);
            case PASSWORD_EXPIRED ->
                    this.authError(SecurityMessageResourceService.USER_ACCOUNT_PASS_EXPIRED, user.getLockedDueTo());
            default -> this.authError(SecurityMessageResourceService.USER_ACCOUNT_BLOCKED);
        };
    }

    private Mono<User> checkUserLockStatus(User user) {

        if (user.getLockedUntil().isBefore(LocalDateTime.now()))
            return this.userService
                    .unlockUserInternal(user.getId())
                    .flatMap(unblocked -> Boolean.TRUE.equals(unblocked)
                            ? Mono.just(user)
                            : this.authError(SecurityMessageResourceService.USER_ACCOUNT_BLOCKED));

        return this.authError(
                SecurityMessageResourceService.USER_ACCOUNT_BLOCKED_LIMIT,
                user.getLockedDueTo(),
                user.getLockedUntil() == null
                        ? 5
                        : user.getLockedUntil()
                        .minusMinutes(LocalDateTime.now().getMinute())
                        .getMinute());
    }

    private <T extends AbstractPolicy> Mono<Boolean> checkPassword(
            String passwordString, String appCode, User user, T policy, AuthenticationPasswordType passwordType) {

        return FlatMapUtil.flatMapMono(
                        () -> switch (passwordType) {
                            case PASSWORD -> user.isPasswordHashed()
                                    ? Mono.just(
                                    pwdEncoder.matches(user.getId() + passwordString, user.getPassword()))
                                    : Mono.just(StringUtil.safeEquals(passwordString, user.getPassword()));
                            case PIN -> user.isPinHashed()
                                    ? Mono.just(pwdEncoder.matches(user.getId() + passwordString, user.getPin()))
                                    : Mono.just(StringUtil.safeEquals(passwordString, user.getPin()));
                            case OTP -> this.otpService.verifyOtpInternal(
                                    appCode,
                                    user,
                                    new OtpVerificationRequest()
                                            .setPurpose(OtpPurpose.PASSWORD_RESET)
                                            .setOtp(passwordString));
                        },
                        isValid -> Boolean.FALSE.equals(isValid)
                                ? checkFailedAttempts(user, policy, passwordType)
                                : Mono.just(Boolean.TRUE))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "AuthenticationService.checkPassword"));
    }

    private <T extends AbstractPolicy> Mono<Boolean> checkFailedAttempts(
            User user, T policy, AuthenticationPasswordType passwordType) {

        if (policy.getNoFailedAttempts() != null && compareFailedAttempts(user, policy, passwordType) >= 0) {

            soxLogService.createLog(
                    user.getId(),
                    SecuritySoxLogActionName.LOGIN,
                    SecuritySoxLogObjectName.USER,
                    "Failed password attempts are more than the configuration");

            return FlatMapUtil.flatMapMono(
                    () -> this.lockUser(
                            user, LocalDateTime.now().plusMinutes(policy.getUserLockTime()), passwordType.getName()),
                    userLocked -> this.authError(SecurityMessageResourceService.USER_ACCOUNT_BLOCKED));
        }
        return handleAuthFailure(user, policy, passwordType);
    }

    private <T extends AbstractPolicy> int compareFailedAttempts(
            User user, T policy, AuthenticationPasswordType passwordType) {

        return switch (passwordType) {
            case PASSWORD -> user.getNoFailedAttempt().compareTo(policy.getNoFailedAttempts());
            case PIN -> user.getNoPinFailedAttempt().compareTo(policy.getNoFailedAttempts());
            case OTP -> user.getNoOtpFailedAttempt().compareTo(policy.getNoFailedAttempts());
        };
    }

    private Mono<Boolean> lockUser(User user, LocalDateTime lockUntil, String lockedDueTo) {
        return userService
                .lockUserInternal(user.getId(), lockUntil, lockedDueTo)
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "AuthenticationService.lockUser"));
    }

    private <T extends AbstractPolicy> Mono<Boolean> handleAuthFailure(
            User user, T policy, AuthenticationPasswordType passwordType) {

        return userService.increaseFailedAttempt(user.getId(), passwordType).flatMap(increasedAttempts -> {
            int remainingAttempts = Math.max(policy.getNoFailedAttempts().intValue() - increasedAttempts, 0);

            soxLogService.createLog(
                    user.getId(),
                    SecuritySoxLogActionName.UPDATE,
                    SecuritySoxLogObjectName.USER,
                    "Given Password is mismatching with existing.");

            return this.authError(
                    SecurityMessageResourceService.USER_PASSWORD_INVALID_ATTEMPTS,
                    passwordType.getName(),
                    remainingAttempts);
        });
    }

    private <T> Mono<T> authError(String message, Object... params) {
        return resourceService
                .getMessage(message, params)
                .handle((msg, sink) -> sink.error(new GenericException(HttpStatus.FORBIDDEN, msg)));
    }

    private Mono<Boolean> resetUserAttempts(User user, AuthenticationPasswordType passwordType) {

        if (passwordType.equals(AuthenticationPasswordType.OTP))
            return userService
                    .resetFailedAttempt(user.getId(), passwordType)
                    .flatMap(reset -> userService.resetResendAttempt(user.getId()))
                    .contextWrite(Context.of(LogUtil.METHOD_NAME, "AuthenticationService.resetUserAttempts"));

        return userService
                .resetFailedAttempt(user.getId(), passwordType)
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "AuthenticationService.resetUserAttempts"));
    }

    private Mono<AuthenticationResponse> logAndMakeToken(
            boolean rememberMe,
            boolean isCookie,
            ServerHttpRequest request,
            ServerHttpResponse response,
            User user,
            Client client,
            Client linClient) {

        soxLogService.createLog(
                user.getId(), SecuritySoxLogActionName.LOGIN, SecuritySoxLogObjectName.USER, "Successful");

        return makeToken(
                rememberMe,
                isCookie,
                request,
                response,
                user,
                client,
                linClient);
    }

    private Mono<AuthenticationResponse> makeToken(
            boolean rememberMe,
            boolean isCookie,
            ServerHttpRequest request,
            ServerHttpResponse response,
            User user,
            Client client,
            Client linClient) {

        int timeInMinutes = rememberMe ? rememberMeExpiryInMinutes : client.getTokenValidityMinutes();
        if (timeInMinutes <= 0) timeInMinutes = this.defaultExpiryInMinutes;

        String host = request.getURI().getHost();
        String port = "" + request.getURI().getPort();

        List<String> forwardedHost = request.getHeaders().get("X-Forwarded-Host");

        if (forwardedHost != null && !forwardedHost.isEmpty()) host = forwardedHost.getFirst();

        List<String> forwardedPort = request.getHeaders().get("X-Forwarded-Port");

        if (forwardedPort != null && !forwardedPort.isEmpty()) port = forwardedPort.getFirst();

        Tuple2<String, LocalDateTime> token = JWTUtil.generateToken(JWTGenerateTokenParameters.builder()
                .userId(user.getId().toBigInteger())
                .secretKey(tokenKey)
                .expiryInMin(timeInMinutes)
                .host(host)
                .port(port)
                .loggedInClientId(linClient.getId().toBigInteger())
                .loggedInClientCode(linClient.getCode())
                .build());

        if (isCookie)
            response.addCookie(ResponseCookie.from("Authentication", token.getT1())
                    .path("/")
                    .maxAge(Duration.ofMinutes(timeInMinutes))
                    .build());

        String address = getRemoteAddressFrom(request);

        return tokenService
                .create(new TokenObject()
                        .setUserId(user.getId())
                        .setToken(token.getT1())
                        .setPartToken(
                                token.getT1().length() < 50
                                        ? token.getT1()
                                        : token.getT1().substring(token.getT1().length() - 50))
                        .setExpiresAt(token.getT2())
                        .setIpAddress(address))
                .map(t -> new AuthenticationResponse()
                        .setUser(user.toContextUser())
                        .setClient(client)
                        .setLoggedInClientCode(linClient.getCode())
                        .setLoggedInClientId(linClient.getId().toBigInteger())
                        .setAccessToken(token.getT1())
                        .setAccessTokenExpiryAt(token.getT2()))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "AuthenticationService.makeToken"));
    }

    @Nullable
    private static String getRemoteAddressFrom(ServerHttpRequest request) {
        List<String> realIp = request.getHeaders().get("X-Real-IP");
        String address = request.getRemoteAddress() != null ? request.getRemoteAddress().getAddress().getHostAddress() : null;
        if (realIp != null && !realIp.isEmpty()) {
            address = realIp.getFirst();
        }
        return address;
    }

    @Override
    public Mono<Authentication> getAuthentication(
            boolean basic, String bearerToken, String clientCode, String appCode, ServerHttpRequest request) {

        if (StringUtil.safeIsBlank(bearerToken)) return this.makeAnonySpringAuthentication(request);

        return FlatMapUtil.flatMapMonoWithNull(
                        () -> cacheService.get(CACHE_NAME_TOKEN, bearerToken).map(ContextAuthentication.class::cast),
                        cachedCA -> basic ? Mono.empty() : checkTokenOrigin(request, this.extractClaims(bearerToken)),
                        (cachedCA, claims) -> {
                            if (cachedCA != null) return Mono.just(cachedCA);

                            return getAuthenticationIfNotInCache(appCode, basic, bearerToken, request);
                        })
                .onErrorResume(e -> this.makeAnonySpringAuthentication(request))
                .flatMap(e -> {
                    if (e instanceof ContextAuthentication ca && ca.isAuthenticated()) {
                        return this.userService
                                .getUserAuthorities(
                                        appCode,
                                        ULong.valueOf(ca.getUser().getClientId()),
                                        ULong.valueOf(ca.getUser().getId()))
                                .map(ca.getUser()::setStringAuthorities)
                                .map(x -> e);
                    }
                    return Mono.just(e);
                })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "AuthenticationService.getAuthentication"));
    }

    private Mono<Authentication> getAuthenticationIfNotInCache(
            String appCode, boolean basic, String bearerToken, ServerHttpRequest request) {

        if (!basic) {

            final var claims = extractClaims(bearerToken);

            return FlatMapUtil.flatMapMono(
                            () -> tokenService
                                    .readAllFilter(new FilterCondition()
                                            .setField("partToken")
                                            .setOperator(FilterConditionOperator.EQUALS)
                                            .setValue(toPartToken(bearerToken)))
                                    .filter(e -> e.getToken().equals(bearerToken))
                                    .take(1)
                                    .single(),
                            token -> this.makeSpringAuthentication(appCode, request, claims, token),
                            (token, ca) -> {
                                if (claims.isOneTime())
                                    return tokenService.delete(token.getId()).map(e -> ca);

                                return cacheService.put(CACHE_NAME_TOKEN, ca, bearerToken);
                            })
                    .contextWrite(Context.of(
                            LogUtil.METHOD_NAME, "AuthenticationService.getAuthenticationIfNotInCache [Bearer]"))
                    .map(Authentication.class::cast)
                    .switchIfEmpty(Mono.error(new GenericException(
                            HttpStatus.UNAUTHORIZED,
                            resourceService.getDefaultLocaleMessage(SecurityMessageResourceService.UNKNOWN_TOKEN))));

        } else {

            String token = bearerToken;
            String finToken;
            if (token.toLowerCase().startsWith("basic ")) {
                token = token.substring(6);
                finToken = bearerToken;
            } else {
                finToken = "Basic " + bearerToken;
            }
            token = new String(Base64.getDecoder().decode(token));

            String username = token.substring(0, token.indexOf(':'));
            String password = token.substring(token.indexOf(':') + 1);

            String reqAppCode = request.getHeaders().getFirst(AppService.AC);
            String clientCode = request.getHeaders().getFirst(ClientService.CC);

            return FlatMapUtil.flatMapMono(
                            () -> this.userService.findNonDeletedUserNClient(
                                    username, null, clientCode, reqAppCode, AuthenticationIdentifierType.USER_NAME),
                            tup -> this.userService
                                    .checkUserAndClient(tup, clientCode)
                                    .flatMap(BooleanUtil::safeValueOfWithEmpty),
                            (tup, linCCheck) -> this.checkUserStatus(tup.getT3()),
                            (tup, linCCheck, user) -> this.clientService.getClientAppPolicy(
                                    tup.getT2().getId(), reqAppCode, AuthenticationPasswordType.PASSWORD),
                            (tup, linCCheck, user, policy) -> this.checkPassword(
                                    password, null, user, policy, AuthenticationPasswordType.PASSWORD),
                            (tup, linCCheck, user, policy, passwordChecked) -> Mono.just(new ContextAuthentication(
                                    user.toContextUser(),
                                    true,
                                    tup.getT1().getId().toBigInteger(),
                                    tup.getT1().getCode(),
                                    tup.getT2().getTypeCode(),
                                    tup.getT2().getCode(),
                                    finToken,
                                    LocalDateTime.now().plusYears(1),
                                    clientCode,
                                    reqAppCode)))
                    .contextWrite(Context.of(
                            LogUtil.METHOD_NAME, "AuthenticationService.getAuthenticationIfNotInCache [Basic]"))
                    .map(Authentication.class::cast)
                    .switchIfEmpty(Mono.error(new GenericException(
                            HttpStatus.UNAUTHORIZED,
                            resourceService.getDefaultLocaleMessage(SecurityMessageResourceService.UNKNOWN_TOKEN))));
        }
    }

    private JWTClaims extractClaims(String bearerToken) {

        JWTClaims claims;
        try {
            claims = JWTUtil.getClaimsFromToken(this.tokenKey, bearerToken);
        } catch (Exception ex) {
            throw new GenericException(
                    HttpStatus.UNAUTHORIZED,
                    resourceService.getDefaultLocaleMessage(SecurityMessageResourceService.TOKEN_EXPIRED),
                    ex);
        }

        return claims;
    }

    private String toPartToken(String bearerToken) {
        return bearerToken.length() > 50 ? bearerToken.substring(bearerToken.length() - 50) : bearerToken;
    }

    private Mono<ContextAuthentication> makeSpringAuthentication(
            String appCode, ServerHttpRequest request, JWTClaims jwtClaims, TokenObject tokenObject) {

        return FlatMapUtil.flatMapMono(
                        () -> checkTokenOrigin(request, jwtClaims),
                        claims -> this.userService.readInternal(appCode, tokenObject.getUserId()),
                        (claims, u) -> this.clientService.getClientTypeNCode(u.getClientId()),
                        (claims, u, typ) -> Mono.just(new ContextAuthentication(
                                u.toContextUser(),
                                true,
                                claims.getLoggedInClientId(),
                                claims.getLoggedInClientCode(),
                                typ.getT1(),
                                typ.getT2(),
                                tokenObject.getToken(),
                                tokenObject.getExpiresAt(),
                                null,
                                null)))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "AuthenticationService.makeSpringAuthentication"));
    }

    private Mono<Authentication> makeAnonySpringAuthentication(ServerHttpRequest request) {

        List<String> clientCode = request.getHeaders().get(ClientService.CC);

        Mono<Client> loggedInClient = ((clientCode != null && !clientCode.isEmpty())
                ? this.clientService.getClientBy(clientCode.getFirst())
                : this.clientService.getClientBy(request))
                .switchIfEmpty(this.resourceService.throwMessage(
                        msg -> new GenericException(HttpStatus.UNAUTHORIZED, msg),
                        SecurityMessageResourceService.UNKNOWN_CLIENT));

        return loggedInClient.map(e -> new ContextAuthentication(
                new ContextUser()
                        .setId(BigInteger.ZERO)
                        .setCreatedBy(BigInteger.ZERO)
                        .setUpdatedBy(BigInteger.ZERO)
                        .setCreatedAt(LocalDateTime.now())
                        .setUpdatedAt(LocalDateTime.now())
                        .setClientId(e.getId().toBigInteger())
                        .setUserName("_Anonymous")
                        .setEmailId("nothing@nothing")
                        .setPhoneNumber("+910000000000")
                        .setFirstName("Anonymous")
                        .setLastName("")
                        .setLocaleCode("en")
                        .setPassword("")
                        .setPasswordHashed(false)
                        .setAccountNonExpired(true)
                        .setAccountNonLocked(true)
                        .setCredentialsNonExpired(true)
                        .setNoFailedAttempt((short) 0)
                        .setStringAuthorities(List.of("Authorities._Anonymous")),
                false,
                e.getId().toBigInteger(),
                e.getCode(),
                e.getTypeCode(),
                e.getCode(),
                "",
                LocalDateTime.MAX,
                null,
                null));
    }

    private Mono<JWTClaims> checkTokenOrigin(ServerHttpRequest request, JWTClaims jwtClaims) {

        String host = request.getURI().getHost();

        List<String> forwardedHost = request.getHeaders().get("X-Forwarded-Host");

        if (forwardedHost != null && !forwardedHost.isEmpty()) {
            host = forwardedHost.getFirst();
        }

        if (!host.equals(jwtClaims.getHostName())) {
            return resourceService.throwMessage(
                    msg -> new GenericException(HttpStatus.UNAUTHORIZED, msg),
                    SecurityMessageResourceService.UNKNOWN_TOKEN);
        }

        return Mono.just(jwtClaims);
    }

    public Mono<AuthenticationResponse> refreshToken(ServerHttpRequest request) {

        return FlatMapUtil.flatMapMono(
                        SecurityContextUtil::getUsersContextAuthentication,
                        ca -> {
                            if (!ca.isAuthenticated())
                                return Mono.error(new GenericException(HttpStatus.UNAUTHORIZED, "Unauthorized"));

                            return this.clientService.getClientInfoById(
                                    ca.getUser().getClientId());
                        },
                        (ca, client) -> {
                            if (LocalDateTime.ofInstant(
                                            Instant.now().plus(Duration.ofMinutes(this.defaultRefreshInMinutes)),
                                            ZoneOffset.UTC)
                                    .isAfter(ca.getAccessTokenExpiryAt()))
                                return this.revoke(request).map(e -> true);

                            return Mono.just(false);
                        },
                        (ca, client, revoked) -> {
                            if (Boolean.TRUE.equals(revoked)) return this.generateNewToken(ca, request, client);

                            return Mono.just(new AuthenticationResponse()
                                    .setUser(ca.getUser())
                                    .setClient(client)
                                    .setLoggedInClientCode(ca.getLoggedInFromClientCode())
                                    .setLoggedInClientId(ca.getLoggedInFromClientId())
                                    .setAccessToken(ca.getAccessToken())
                                    .setAccessTokenExpiryAt(ca.getAccessTokenExpiryAt()));
                        })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "AuthenticationService.refreshToken"));
    }

    private Mono<AuthenticationResponse> generateNewToken(
            ContextAuthentication ca, ServerHttpRequest request, Client client) {

        return FlatMapUtil.flatMapMono(
                        () -> {
                            JWTClaims claims = JWTUtil.getClaimsFromToken(tokenKey, ca.getAccessToken());

                            JWTGenerateTokenParameters params = JWTGenerateTokenParameters.builder()
                                    .userId(ca.getUser().getId())
                                    .secretKey(tokenKey)
                                    .expiryInMin(
                                            client.getTokenValidityMinutes() <= 0
                                                    ? this.defaultExpiryInMinutes
                                                    : client.getTokenValidityMinutes())
                                    .host(claims.getHostName())
                                    .port(claims.getPort())
                                    .loggedInClientId(claims.getLoggedInClientId())
                                    .loggedInClientCode(claims.getLoggedInClientCode())
                                    .build();

                            return Mono.just(JWTUtil.generateToken(params));
                        },
                        token -> {
                            InetSocketAddress inetAddress = request.getRemoteAddress();
                            final String hostAddress = inetAddress == null ? null : inetAddress.getHostString();

                            return tokenService.create(new TokenObject()
                                    .setUserId(ULong.valueOf(ca.getUser().getId()))
                                    .setToken(token.getT1())
                                    .setPartToken(
                                            token.getT1().length() < 50
                                                    ? token.getT1()
                                                    : token.getT1()
                                                    .substring(token.getT1()
                                                            .length()
                                                            - 50))
                                    .setExpiresAt(token.getT2())
                                    .setIpAddress(hostAddress));
                        },
                        (token, t) -> Mono.just(new AuthenticationResponse()
                                .setUser(ca.getUser())
                                .setClient(client)
                                .setLoggedInClientCode(ca.getLoggedInFromClientCode())
                                .setLoggedInClientId(ca.getLoggedInFromClientId())
                                .setAccessToken(token.getT1())
                                .setAccessTokenExpiryAt(token.getT2())))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "AuthenticationService.generateNewToken"));
    }

    private String fillValues(String url, String token) {
        if (StringUtil.safeIsBlank(url)) return "";

        String env = this.appCodeSuffix == null ? "" : this.appCodeSuffix;
        env = env.replace(".", "");

        return url.replace("{env}", env)
                .replace("{envDotPrefix}", "." + env)
                .replace("{envDotSuffix}", env + ".")
                .replace("{token}", token);
    }

    public Mono<Map<String, String>> makeOneTimeToken(MakeOneTimeTimeTokenRequest request, ServerHttpRequest httpRequest) {

        return FlatMapUtil.flatMapMono(

                        SecurityContextUtil::getUsersContextAuthentication,

                        ca -> this.oneTimeTokenService.create(
                                new OneTimeToken()
                                        .setIpAddress(getRemoteAddressFrom(httpRequest))
                                        .setUserId(ULong.valueOf(ca.getUser().getId()))),

                        (ca, token) -> Mono.just(Map.of("token", token.getToken(),
                                "url", this.fillValues(request.getCallbackUrl(), token.getToken())))
                )
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "AuthenticationService.makeOneTimeToken"));
    }

    public Mono<AuthenticationResponse> authenticateWithOneTimeToken(String token, ServerHttpRequest request, ServerHttpResponse response) {

        String appCode = request.getHeaders().getFirst(AppService.AC);
        String clientCode = request.getHeaders().getFirst(ClientService.CC);


        return FlatMapUtil.flatMapMono(
                        () -> this.oneTimeTokenService.getUserId(token),

                        userId ->
                                this.userService
                                        .findNonDeletedUserNClient(
                                                null,
                                                userId,
                                                clientCode,
                                                appCode,
                                                null)
                                        .flatMap(tup -> this.profileService
                                                .checkIfUserHasAnyProfile(tup.getT3().getId(), appCode)
                                                .flatMap(e -> {
                                                    if (BooleanUtil.safeValueOf(e)) return Mono.just(tup);
                                                    return Mono.empty();
                                                })),
                        (userId, tup) -> this.userService
                                .checkUserAndClient(tup, clientCode)
                                .flatMap(BooleanUtil::safeValueOfWithEmpty),
                        (userId, tup, linCCheck) -> this.checkUserStatus(tup.getT3()),
                        (userId, tup, linCCheck, user) ->
                                logAndMakeToken(false, false, request, response, user, tup.getT2(), tup.getT1()))
                .switchIfEmpty(this.authError(SecurityMessageResourceService.USER_CREDENTIALS_MISMATCHED))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "AuthenticationService.authenticateWithOneTimeToken"));
    }
}
