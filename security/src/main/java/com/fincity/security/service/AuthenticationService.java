package com.fincity.security.service;

import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;

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
import com.fincity.security.dto.ClientPasswordPolicy;
import com.fincity.security.dto.SoxLog;
import com.fincity.security.dto.TokenObject;
import com.fincity.security.dto.User;
import com.fincity.security.jooq.enums.SecuritySoxLogActionName;
import com.fincity.security.jooq.enums.SecuritySoxLogObjectName;
import com.fincity.security.model.AuthenticationIdentifierType;
import com.fincity.security.model.AuthenticationRequest;
import com.fincity.security.model.AuthenticationResponse;
import com.fincity.security.service.appregistration.AppRegistrationIntegrationTokenService;

import reactor.core.publisher.Mono;
import reactor.util.context.Context;
import reactor.util.function.Tuple2;

@Service
public class AuthenticationService implements IAuthenticationService {

	private final UserService userService;

	private final ClientService clientService;

	private final TokenService tokenService;

	private final SecurityMessageResourceService resourceService;

	private final SoxLogService soxLogService;

	private final PasswordEncoder pwdEncoder;

	private final CacheService cacheService;

	private final AppRegistrationIntegrationTokenDao integrationTokenDao;

	private final AppRegistrationIntegrationTokenService appRegistrationIntegrationTokenService;

	public AuthenticationService(UserService userService, ClientService clientService, TokenService tokenService,
			SecurityMessageResourceService resourceService, SoxLogService soxLogService, PasswordEncoder pwdEncoder,
			CacheService cacheService, AppRegistrationIntegrationTokenDao integrationTokenDao,
			AppRegistrationIntegrationTokenService appRegistrationIntegrationTokenService) {
		this.userService = userService;
		this.clientService = clientService;
		this.tokenService = tokenService;
		this.resourceService = resourceService;
		this.soxLogService = soxLogService;
		this.pwdEncoder = pwdEncoder;
		this.cacheService = cacheService;
		this.integrationTokenDao = integrationTokenDao;
		this.appRegistrationIntegrationTokenService = appRegistrationIntegrationTokenService;
	}

	@Value("${jwt.key}")
	private String tokenKey;

	@Value("${jwt.token.rememberme.expiry}")
	private Integer remembermeExpiryInMinutes;

	@Value("${jwt.token.default.expiry}")
	private Integer defaultExpiryInMinutes;

	@Value("${jwt.token.default.refresh:10}")
	private Integer defaultRefreshInMinutes;

	public Mono<Integer> revoke(ServerHttpRequest request) {

		String bearerToken = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

		if (bearerToken == null || bearerToken.isBlank()) {
			HttpCookie cookie = request.getCookies().getFirst(HttpHeaders.AUTHORIZATION);
			if (cookie != null)
				bearerToken = cookie.getValue();
		}

		if (bearerToken != null) {

			bearerToken = bearerToken.trim();

			if (bearerToken.startsWith("Bearer ")) {
				bearerToken = bearerToken.substring(7);
			} else if (bearerToken.startsWith("Basic ")) {
				bearerToken = bearerToken.substring(6);
			}
		}

		if (bearerToken == null)
			return Mono.just(1);

		final String finToken = bearerToken;

		cacheService.evict(CACHE_NAME_TOKEN, finToken).subscribe();

		return tokenService
				.readAllFilter(new FilterCondition().setField("partToken").setOperator(FilterConditionOperator.EQUALS)
						.setValue(toPartToken(finToken)))
				.filter(e -> e.getToken().equals(finToken)).map(TokenObject::getId).collectList()
				.flatMap(e -> e.isEmpty() ? Mono.empty() : Mono.just(e.get(0))).flatMap(tokenService::delete)
				.defaultIfEmpty(1);
	}

	public Mono<AuthenticationResponse> authenticate(AuthenticationRequest authRequest, ServerHttpRequest request,
			ServerHttpResponse response) {

		String appCode = request.getHeaders().getFirst("appCode");

		String clientCode = request.getHeaders().getFirst("clientCode");

		if (authRequest.getIdentifierType() == null) {
			authRequest.setIdentifierType(
					StringUtil.safeIsBlank(authRequest.getUserName()) || authRequest.getUserName().indexOf('@') == -1
							? AuthenticationIdentifierType.USER_NAME
							: AuthenticationIdentifierType.EMAIL_ID);
		}

		return FlatMapUtil.flatMapMono(

				() -> this.userService.findUserNClient(authRequest.getUserName(), authRequest.getUserId(), clientCode,
						appCode,
						authRequest.getIdentifierType(), true),
				tup -> {
					String linClientCode = tup.getT1().getCode();
					return Mono.justOrEmpty(linClientCode.equals("SYSTEM") || clientCode.equals(linClientCode)
							|| tup.getT1().getId().equals(tup.getT2().getId()) ? true : null);
				},

				(tup, linCCheck) -> this.checkPassword(authRequest.getPassword(), tup.getT3()),

				(tup, linCCheck, passwordChecked) -> this.clientService.getClientPasswordPolicy(tup.getT2().getId())
						.flatMap(policy -> this.checkFailedAttempts(tup.getT3(), policy)).defaultIfEmpty(1),

				(tup, linCCheck, passwordChecked, j) -> {

					User user = tup.getT3();

					userService.resetFailedAttempt(user.getId()).subscribe();

					soxLogService
							.create(new SoxLog().setObjectId(user.getId()).setActionName(SecuritySoxLogActionName.LOGIN)
									.setObjectName(SecuritySoxLogObjectName.USER).setDescription("Successful"))
							.subscribe();

					InetSocketAddress inetAddress = request.getRemoteAddress();
					final String hostAddress = inetAddress == null ? null : inetAddress.getHostString();

					return makeToken(authRequest, request, response, hostAddress, user, tup.getT2(), tup.getT1());
				}).contextWrite(Context.of(LogUtil.METHOD_NAME, "AuthenticationService.authenticate"))
				.switchIfEmpty(Mono.defer(this::credentialError));
	}

	public Mono<AuthenticationResponse> authenticateWSocial(AuthenticationRequest authRequest,
			ServerHttpRequest request,
			ServerHttpResponse response) {

		if (authRequest.getSocialRegisterState() == null) {
			return this.resourceService.throwMessage(
					msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
					SecurityMessageResourceService.SOCIAL_LOGIN_FAILED);
		}

		String appCode = request.getHeaders().getFirst("appCode");

		String clientCode = request.getHeaders().getFirst("clientCode");

		if (authRequest.getIdentifierType() == null) {
			authRequest.setIdentifierType(AuthenticationIdentifierType.EMAIL_ID);
		}

		return FlatMapUtil.flatMapMono(

				() -> this.appRegistrationIntegrationTokenService
						.verifyIntegrationState(authRequest.getSocialRegisterState()),

				appRegIntgToken -> Mono.just(appRegIntgToken.getUsername().equals(authRequest.getUserName()))
						.flatMap(BooleanUtil::safeValueOfWithEmpty),

				(appRegIntgToken, usernameChecked) -> this.userService.findUserNClient(authRequest.getUserName(),
						authRequest.getUserId(), clientCode,
						appCode, authRequest.getIdentifierType(), true),

				(appRegIntgToken, usernameChecked, tup) -> {
					String linClientCode = tup.getT1().getCode();
					return Mono.justOrEmpty(linClientCode.equals("SYSTEM") || clientCode.equals(linClientCode)
							|| tup.getT1().getId().equals(tup.getT2().getId()) ? true : null);
				},

				(appRegIntgToken, usernameChecked, tup, linCCheck) -> {

					User user = tup.getT3();

					appRegIntgToken.setCreatedBy(user.getId());
					appRegIntgToken.setUpdatedBy(user.getId());

					this.integrationTokenDao.update(appRegIntgToken);

					soxLogService
							.create(new SoxLog().setObjectId(user.getId()).setActionName(SecuritySoxLogActionName.LOGIN)
									.setObjectName(SecuritySoxLogObjectName.USER).setDescription("Successful"))
							.subscribe();

					InetSocketAddress inetAddress = request.getRemoteAddress();
					final String hostAddress = inetAddress == null ? null : inetAddress.getHostString();

					return makeToken(authRequest, request, response, hostAddress, user, tup.getT2(), tup.getT1());

				}).contextWrite(Context.of(LogUtil.METHOD_NAME, "AuthenticationService.authenticateWSocial"))
				.switchIfEmpty(Mono.defer(this::credentialError)).log();
	}

	private Mono<AuthenticationResponse> makeToken(AuthenticationRequest authRequest, ServerHttpRequest request,
			ServerHttpResponse response, final String setAddress, User u, Client c, Client linClient) {

		int timeInMinutes = authRequest.isRememberMe() ? remembermeExpiryInMinutes : c.getTokenValidityMinutes();
		if (timeInMinutes <= 0)
			timeInMinutes = this.defaultExpiryInMinutes;

		String host = request.getURI().getHost();
		String port = "" + request.getURI().getPort();

		List<String> forwardedHost = request.getHeaders().get("X-Forwarded-Host");

		if (forwardedHost != null && !forwardedHost.isEmpty()) {
			host = forwardedHost.get(0);
		}

		List<String> forwardedPort = request.getHeaders().get("X-Forwarded-Port");

		if (forwardedPort != null && !forwardedPort.isEmpty()) {
			port = forwardedPort.get(0);
		}

		Tuple2<String, LocalDateTime> token = JWTUtil.generateToken(JWTGenerateTokenParameters.builder()
				.userId(u.getId().toBigInteger()).secretKey(tokenKey).expiryInMin(timeInMinutes).host(host).port(port)
				.loggedInClientId(linClient.getId().toBigInteger()).loggedInClientCode(linClient.getCode()).build());

		if (authRequest.isCookie())
			response.addCookie(ResponseCookie.from("Authentication", token.getT1()).path("/")
					.maxAge(Duration.ofMinutes(timeInMinutes)).build());

		return tokenService
				.create(new TokenObject().setUserId(u.getId()).setToken(token.getT1())
						.setPartToken(
								token.getT1().length() < 50 ? token.getT1()
										: token.getT1().substring(token.getT1().length() - 50))
						.setExpiresAt(token.getT2()).setIpAddress(setAddress))
				.map(t -> new AuthenticationResponse().setUser(u.toContextUser()).setClient(c)
						.setLoggedInClientCode(linClient.getCode())
						.setLoggedInClientId(linClient.getId().toBigInteger())
						.setAccessToken(token.getT1()).setAccessTokenExpiryAt(token.getT2()));
	}

	private Mono<Integer> checkFailedAttempts(User u, ClientPasswordPolicy pol) {

		if (pol.getNoFailedAttempts() != null && pol.getNoFailedAttempts().shortValue() <= u.getNoFailedAttempt()) {

			soxLogService.create(new SoxLog().setObjectId(u.getId()).setActionName(SecuritySoxLogActionName.LOGIN)
					.setObjectName(SecuritySoxLogObjectName.USER)
					.setDescription("Failed password attempts are more than the configuration")).subscribe();

			return this.credentialError().map(e -> 1);
		}

		return Mono.just(1);
	}

	private Mono<Boolean> checkPassword(String password, User u) {

		if (u.isPasswordHashed()) {
			if (pwdEncoder.matches(u.getId() + password, u.getPassword()))
				return Mono.just(true);
		} else if (StringUtil.safeEquals(password, u.getPassword()))
			return Mono.just(true);

		userService.increaseFailedAttempt(u.getId()).subscribe();

		soxLogService.createLog(u.getId(), SecuritySoxLogActionName.UPDATE, SecuritySoxLogObjectName.USER,
				"Given Password is mismatching with existing.");

		return this.credentialError().map(e -> false);
	}

	private Mono<? extends AuthenticationResponse> credentialError() {

		return resourceService.getMessage(SecurityMessageResourceService.USER_CREDENTIALS_MISMATCHED).map(msg -> {
			throw new GenericException(HttpStatus.FORBIDDEN, msg);
		});

	}

	@Override
	public Mono<Authentication> getAuthentication(boolean basic, String bearerToken, String clientcode, String appCode,
			ServerHttpRequest request) {

		if (StringUtil.safeIsBlank(bearerToken)) {
			return this.makeAnonySpringAuthentication(request);
		}

		return FlatMapUtil.flatMapMonoWithNull(

				() -> cacheService.get(CACHE_NAME_TOKEN, bearerToken).map(ContextAuthentication.class::cast),

				cachedCA -> basic ? Mono.empty() : checkTokenOrigin(request, this.extractClamis(bearerToken)),

				(cachedCA, claims) -> {

					if (cachedCA != null)
						return Mono.just(cachedCA);

					return getAuthenticationIfNotInCache(basic, bearerToken, request);
				})
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "AuthenticationService.getAuthentication"))
				.onErrorResume(e -> this.makeAnonySpringAuthentication(request));
	}

	private Mono<Authentication> getAuthenticationIfNotInCache(boolean basic, String bearerToken,
			ServerHttpRequest request) {

		if (!basic) {

			final var claims = extractClamis(bearerToken);

			return FlatMapUtil.flatMapMono(

					() -> tokenService.readAllFilter(new FilterCondition().setField("partToken")
							.setOperator(FilterConditionOperator.EQUALS).setValue(toPartToken(bearerToken)))
							.filter(e -> e.getToken().equals(bearerToken)).take(1).single(),

					token -> this.makeSpringAuthentication(request, claims, token),

					(token, ca) -> {

						if (claims.isOneTime())
							return tokenService.delete(token.getId()).map(e -> ca);

						return cacheService.put(CACHE_NAME_TOKEN, ca, bearerToken);
					})
					.contextWrite(
							Context.of(LogUtil.METHOD_NAME,
									"AuthenticationService.getAuthenticationIfNotInCache [Bearer]"))
					.map(Authentication.class::cast)
					.switchIfEmpty(Mono.error(new GenericException(HttpStatus.UNAUTHORIZED,
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

			String appCode = request.getHeaders().getFirst("appCode");
			String clientCode = request.getHeaders().getFirst("clientCode");

			return FlatMapUtil.flatMapMono(

					() -> this.userService.findUserNClient(username, null, clientCode, appCode,
							AuthenticationIdentifierType.USER_NAME, true),

					tup -> {
						String linClientCode = tup.getT1().getCode();
						return Mono.justOrEmpty(linClientCode.equals("SYSTEM") || linClientCode.equals(clientCode)
								|| tup.getT1().getId().equals(tup.getT2().getId()) ? true : null);
					},

					(tup, linCCheck) -> this.checkPassword(password, tup.getT3()),

					(tup, linCCheck, passwordChecked) -> {
						return Mono.just(new ContextAuthentication(
								tup.getT3().toContextUser(), true, tup.getT1().getId().toBigInteger(),
								tup.getT1().getCode(), tup.getT2().getTypeCode(), tup.getT2().getCode(), finToken,
								LocalDateTime.now().plusYears(1), clientCode, appCode));
					})
					.contextWrite(Context.of(LogUtil.METHOD_NAME,
							"AuthenticationService.getAuthenticationIfNotInCache [Basic]"))
					.map(Authentication.class::cast)
					.switchIfEmpty(Mono.error(new GenericException(HttpStatus.UNAUTHORIZED,
							resourceService.getDefaultLocaleMessage(SecurityMessageResourceService.UNKNOWN_TOKEN))));
		}
	}

	private JWTClaims extractClamis(String bearerToken) {

		JWTClaims c = null;
		try {
			c = JWTUtil.getClaimsFromToken(this.tokenKey, bearerToken);
		} catch (Exception ex) {
			throw new GenericException(HttpStatus.UNAUTHORIZED,
					resourceService.getDefaultLocaleMessage(SecurityMessageResourceService.TOKEN_EXPIRED), ex);
		}

		return c;
	}

	private String toPartToken(String bearerToken) {
		return bearerToken.length() > 50 ? bearerToken.substring(bearerToken.length() - 50) : bearerToken;
	}

	private Mono<ContextAuthentication> makeSpringAuthentication(ServerHttpRequest request, JWTClaims jwtClaims,
			TokenObject tokenObject) {

		return FlatMapUtil.flatMapMono(

				() -> checkTokenOrigin(request, jwtClaims),

				claims -> this.userService.readInternal(tokenObject.getUserId()),

				(claims, u) -> this.clientService.getClientTypeNCode(u.getClientId()),

				(claims, u,
						typ) -> Mono.just(new ContextAuthentication(u.toContextUser(), true,
								claims.getLoggedInClientId(),
								claims.getLoggedInClientCode(), typ.getT1(), typ.getT2(), tokenObject.getToken(),
								tokenObject.getExpiresAt(), null, null)))
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "AuthenticationService.makeSpringAuthentication"));
	}

	private Mono<Authentication> makeAnonySpringAuthentication(ServerHttpRequest request) {

		List<String> clientCode = request.getHeaders().get("clientCode");

		Mono<Client> loggedInClient = ((clientCode != null && !clientCode.isEmpty())
				? this.clientService.getClientBy(clientCode.get(0))
				: this.clientService.getClientBy(request))
				.switchIfEmpty(
						this.resourceService.throwMessage(msg -> new GenericException(HttpStatus.UNAUTHORIZED, msg),
								SecurityMessageResourceService.UNKNOWN_CLIENT));

		return loggedInClient.map(e -> (Authentication) new ContextAuthentication(
				new ContextUser().setId(BigInteger.ZERO).setCreatedBy(BigInteger.ZERO).setUpdatedBy(BigInteger.ZERO)
						.setCreatedAt(LocalDateTime.now()).setUpdatedAt(LocalDateTime.now())
						.setClientId(e.getId().toBigInteger())
						.setUserName("_Anonymous").setEmailId("nothing@nothing").setPhoneNumber("+910000000000")
						.setFirstName("Anonymous").setLastName("").setLocaleCode("en").setPassword("")
						.setPasswordHashed(false)
						.setAccountNonExpired(true).setAccountNonLocked(true).setCredentialsNonExpired(true)
						.setNoFailedAttempt((short) 0).setStringAuthorities(List.of("Authorities._Anonymous")),
				false, e.getId().toBigInteger(), e.getCode(), e.getTypeCode(), e.getCode(), "", LocalDateTime.MAX, null,
				null));
	}

	private Mono<JWTClaims> checkTokenOrigin(ServerHttpRequest request, JWTClaims jwtClaims) {

		String host = request.getURI().getHost();

		List<String> forwardedHost = request.getHeaders().get("X-Forwarded-Host");

		if (forwardedHost != null && !forwardedHost.isEmpty()) {
			host = forwardedHost.get(0);
		}

		if (!host.equals(jwtClaims.getHostName())) {

			return resourceService.throwMessage(msg -> new GenericException(HttpStatus.UNAUTHORIZED, msg),
					SecurityMessageResourceService.UNKNOWN_TOKEN);
		}

		return Mono.just(jwtClaims);
	}

	public Mono<AuthenticationResponse> refreshToken(ServerHttpRequest request) {

		return FlatMapUtil.flatMapMono(

				SecurityContextUtil::getUsersContextAuthentication,

				ca -> {

					if (!ca.isAuthenticated()) {
						return Mono.error(new GenericException(HttpStatus.UNAUTHORIZED, "Unauthorized"));
					}

					return this.clientService.getClientInfoById(ca.getUser().getClientId());
				},

				(ca, client) -> {

					if (LocalDateTime
							.ofInstant(Instant.now().plus(Duration.ofMinutes(this.defaultRefreshInMinutes)),
									ZoneOffset.UTC)
							.isAfter(ca.getAccessTokenExpiryAt()))
						return this.revoke(request).map(e -> true);

					return Mono.just(false);
				},

				(ca, client, revoked) -> {

					if (revoked.booleanValue())
						return this.generateNewToken(ca, request, client);

					return Mono.just(new AuthenticationResponse().setUser(ca.getUser()).setClient(client)
							.setLoggedInClientCode(ca.getLoggedInFromClientCode())
							.setLoggedInClientId(ca.getLoggedInFromClientId())
							.setAccessToken(ca.getAccessToken()).setAccessTokenExpiryAt(ca.getAccessTokenExpiryAt()));
				}).contextWrite(Context.of(LogUtil.METHOD_NAME, "AuthenticationService.refreshToken"));
	}

	private Mono<AuthenticationResponse> generateNewToken(ContextAuthentication ca, ServerHttpRequest request,
			Client client) {

		return FlatMapUtil.flatMapMono(() -> {
			JWTClaims claims = JWTUtil.getClaimsFromToken(tokenKey, ca.getAccessToken());

			JWTGenerateTokenParameters params = JWTGenerateTokenParameters.builder().userId(ca.getUser().getId())
					.secretKey(tokenKey)
					.expiryInMin(
							client.getTokenValidityMinutes() <= 0 ? this.defaultExpiryInMinutes
									: client.getTokenValidityMinutes())
					.host(claims.getHostName()).port(claims.getPort()).loggedInClientId(claims.getLoggedInClientId())
					.loggedInClientCode(claims.getLoggedInClientCode()).build();

			return Mono.just(JWTUtil.generateToken(params));
		},

				token -> {
					InetSocketAddress inetAddress = request.getRemoteAddress();
					final String hostAddress = inetAddress == null ? null : inetAddress.getHostString();

					return tokenService.create(new TokenObject().setUserId(ULong.valueOf(ca.getUser().getId()))
							.setToken(token.getT1())
							.setPartToken(
									token.getT1().length() < 50 ? token.getT1()
											: token.getT1().substring(token.getT1().length() - 50))
							.setExpiresAt(token.getT2()).setIpAddress(hostAddress));
				},

				(token,
						t) -> Mono.just(new AuthenticationResponse().setUser(ca.getUser()).setClient(client)
								.setLoggedInClientCode(ca.getLoggedInFromClientCode())
								.setLoggedInClientId(ca.getLoggedInFromClientId())
								.setAccessToken(token.getT1()).setAccessTokenExpiryAt(token.getT2()))

		).contextWrite(Context.of(LogUtil.METHOD_NAME, "AuthenticationService.generateNewToken"));
	}

}
