package com.fincity.security.service;

import static com.fincity.nocode.reactor.util.FlatMapUtil.flatMapMono;
import static com.fincity.nocode.reactor.util.FlatMapUtil.flatMapMonoWithNull;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.fincity.saas.common.security.jwt.ContextAuthentication;
import com.fincity.saas.common.security.jwt.JWTClaims;
import com.fincity.saas.common.security.jwt.JWTUtil;
import com.fincity.saas.common.security.util.SecurityContextUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.commons.model.condition.FilterConditionOperator;
import com.fincity.saas.commons.security.service.IAuthenticationService;
import com.fincity.saas.commons.service.CacheService;
import com.fincity.security.dto.Client;
import com.fincity.security.dto.ClientPasswordPolicy;
import com.fincity.security.dto.SoxLog;
import com.fincity.security.dto.TokenObject;
import com.fincity.security.dto.User;
import com.fincity.security.jooq.enums.SecuritySoxLogActionName;
import com.fincity.security.jooq.enums.SecuritySoxLogObjectName;
import com.fincity.security.model.AuthenticationRequest;
import com.fincity.security.model.AuthenticationResponse;
import com.fincity.security.model.RequestUpdatePassword;

import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;

@Service
public class AuthenticationService implements IAuthenticationService {

	@Autowired
	private UserService userService;

	@Autowired
	private ClientService clientService;

	@Autowired
	private TokenService tokenService;

	@Autowired
	private SecurityMessageResourceService resourceService;

	@Autowired
	private SoxLogService soxLogService;

	@Autowired
	private PasswordEncoder pwdEncoder;

	@Autowired
	private CacheService cacheService;

	@Value("${jwt.key}")
	private String tokenKey;

	@Value("${jwt.token.rememberme.expiry}")
	private Integer remembermeExpiryInMinutes;

	@Value("${jwt.token.default.expiry}")
	private Integer defaultExpiryInMinutes;

	@PreAuthorize("hasAuthority('Authorities.Logged_IN')")
	public Mono<Integer> revoke(ServerHttpRequest request) {

		String bearerToken = request.getHeaders()
		        .getFirst(HttpHeaders.AUTHORIZATION);

		if (bearerToken == null || bearerToken.isBlank()) {
			HttpCookie cookie = request.getCookies()
			        .getFirst(HttpHeaders.AUTHORIZATION);
			if (cookie != null)
				bearerToken = cookie.getValue();
		}

		if (bearerToken != null) {

			bearerToken = bearerToken.trim();

			if (bearerToken.startsWith("Bearer ")) {
				bearerToken = bearerToken.substring(7);
			} else if (bearerToken.startsWith("basic ")) {
				bearerToken = bearerToken.substring(6);
			}
		}

		if (bearerToken == null)
			return Mono.just(1);

		final String finToken = bearerToken;

		cacheService.evict(CACHE_NAME_TOKEN, finToken)
		        .subscribe();

		return tokenService.readAllFilter(new FilterCondition().setField("partToken")
		        .setOperator(FilterConditionOperator.EQUALS)
		        .setValue(toPartToken(finToken)))
		        .filter(e -> e.getToken()
		                .equals(finToken))
		        .take(1)
		        .single()
		        .map(TokenObject::getId)
		        .flatMap(tokenService::delete);
	}

	public Mono<AuthenticationResponse> authenticate(AuthenticationRequest authRequest, ServerHttpRequest request,
	        ServerHttpResponse response) {

		List<String> clientCode = request.getHeaders()
		        .get("clientCode");

		Mono<Client> loggedInClient = (clientCode != null && !clientCode.isEmpty())
		        ? this.clientService.getClientBy(clientCode.get(0))
		        : this.clientService.getClientBy(request);

		return flatMapMono(

		        () -> loggedInClient,

		        linClient -> userService.findByClientIdsUserName(linClient.getId(), authRequest.getUserName(),
		                authRequest.getIdentifierType()),

		        // check user status whether active or inactive

		        (linClient, user) -> this.clientService.readInternal(user.getClientId()),

		        // check client status whether active or inactive

		        (linClient, user, client) -> this.checkPassword(authRequest, user),

		        (linClient, user, client, passwordChecked) -> clientService.getClientPasswordPolicy(client.getId()),

		        (linClient, user, client, passwordChecked, policy) -> this.checkFailedAttempts(user, policy),

		        (linClient, user, client, passwordChecked, policy, j) ->
				{

			        userService.resetFailedAttempt(user.getId())
			                .subscribe();

			        soxLogService.create(new SoxLog().setObjectId(user.getId())
			                .setActionName(SecuritySoxLogActionName.LOGIN)
			                .setObjectName(SecuritySoxLogObjectName.USER)
			                .setDescription("Successful"))
			                .subscribe();

			        InetSocketAddress inetAddress = request.getRemoteAddress();
			        final String hostAddress = inetAddress == null ? null : inetAddress.getHostString();

			        return makeToken(authRequest, request, response, hostAddress, user, client, linClient);
		        }).switchIfEmpty(Mono.defer(this::credentialError));
	}

	private Mono<AuthenticationResponse> makeToken(AuthenticationRequest authRequest, ServerHttpRequest request,
	        ServerHttpResponse response, final String setAddress, User u, Client c, Client linClient) {

		int timeInMinutes = authRequest.isRememberMe() ? remembermeExpiryInMinutes : c.getTokenValidityMinutes();
		if (timeInMinutes <= 0)
			timeInMinutes = this.defaultExpiryInMinutes;

		String host = request.getURI()
		        .getHost();
		String port = "" + request.getURI()
		        .getPort();

		List<String> forwardedHost = request.getHeaders()
		        .get("X-Forwarded-Host");

		if (forwardedHost != null && !forwardedHost.isEmpty()) {
			host = forwardedHost.get(0);
		}

		List<String> forwardedPort = request.getHeaders()
		        .get("X-Forwarded-Port");

		if (forwardedPort != null && !forwardedPort.isEmpty()) {
			port = forwardedPort.get(0);
		}

		Tuple2<String, LocalDateTime> token = JWTUtil.generateToken(u.getId()
		        .toBigInteger(), tokenKey, timeInMinutes, host, port,
		        linClient.getId()
		                .toBigInteger(),
		        linClient.getCode());

		if (authRequest.isCookie())
			response.addCookie(ResponseCookie.from("Authentication", token.getT1())
			        .path("/")
			        .maxAge(Duration.ofMinutes(timeInMinutes))
			        .build());

		return tokenService.create(new TokenObject().setUserId(u.getId())
		        .setToken(token.getT1())
		        .setPartToken(token.getT1()
		                .length() < 50 ? token.getT1()
		                        : token.getT1()
		                                .substring(token.getT1()
		                                        .length() - 50))
		        .setExpiresAt(token.getT2())
		        .setIpAddress(setAddress))
		        .map(t -> new AuthenticationResponse().setUser(u.toContextUser())
		                .setAccessToken(token.getT1())
		                .setAccessTokenExpiryAt(token.getT2()));
	}

	private Mono<Object> checkFailedAttempts(User u, ClientPasswordPolicy pol) {

		if (pol.getNoFailedAttempts() != null && pol.getNoFailedAttempts()
		        .shortValue() >= u.getNoFailedAttempt()) {

			soxLogService.create(new SoxLog().setObjectId(u.getId())
			        .setActionName(SecuritySoxLogActionName.LOGIN)
			        .setObjectName(SecuritySoxLogObjectName.USER)
			        .setDescription("Failed password attempts are more than the configuration"))
			        .subscribe();

			return this.credentialError()
			        .map(e -> 1);
		}

		return Mono.just(1);
	}

	private Mono<Boolean> checkPassword(AuthenticationRequest authRequest, User u) {

		if (!u.isPasswordHashed()) {
			if (!authRequest.getPassword()
			        .equals(u.getPassword())) {

				userService.increaseFailedAttempt(u.getId())
				        .subscribe();

				soxLogService.create(new SoxLog().setObjectId(u.getId())
				        .setActionName(SecuritySoxLogActionName.LOGIN)
				        .setObjectName(SecuritySoxLogObjectName.USER)
				        .setDescription("Password mismatch"))
				        .subscribe();
				return this.credentialError()
				        .map(e -> false);

			}
		} else {
			if (!pwdEncoder.matches(u.getId() + authRequest.getPassword(), u.getPassword())) {

				userService.increaseFailedAttempt(u.getId())
				        .subscribe();

				soxLogService.create(new SoxLog().setObjectId(u.getId())
				        .setActionName(SecuritySoxLogActionName.LOGIN)
				        .setObjectName(SecuritySoxLogObjectName.USER)
				        .setDescription("Password mismatch"))
				        .subscribe();

				return this.credentialError()
				        .map(e -> false);
			}
		}

		return Mono.just(true);
	}

	private Mono<? extends AuthenticationResponse> credentialError() {

		return resourceService.getMessage(SecurityMessageResourceService.USER_CREDENTIALS_MISMATCHED)
		        .map(msg ->
				{
			        throw new GenericException(HttpStatus.FORBIDDEN, msg);
		        });

	}

	public Mono<Authentication> getAuthentication(boolean basic, String bearerToken, ServerHttpRequest request) {

		return flatMapMonoWithNull(

		        () -> cacheService.get(CACHE_NAME_TOKEN, bearerToken)
		                .map(ContextAuthentication.class::cast),

		        cachedCA -> checkTokenOrigin(request, this.extractClamis(bearerToken)),

		        (cachedCA, claims) -> cachedCA == null ? getAuthenticationIfNotInCache(basic, bearerToken, request)
		                : Mono.just(cachedCA));
	}

	private Mono<Authentication> getAuthenticationIfNotInCache(boolean basic, String bearerToken,
	        ServerHttpRequest request) {

		if (!basic) {

			final var claims = extractClamis(bearerToken);

			return tokenService.readAllFilter(new FilterCondition().setField("partToken")
			        .setOperator(FilterConditionOperator.EQUALS)
			        .setValue(toPartToken(bearerToken)))
			        .filter(e -> e.getToken()
			                .equals(bearerToken))
			        .take(1)
			        .single()
			        .flatMap(e -> this.makeSpringAuthentication(request, claims, e))
			        .map(e ->
					{
				        cacheService.put(CACHE_NAME_TOKEN, e, bearerToken);
				        return (Authentication) e;
			        })
			        .switchIfEmpty(Mono.error(new GenericException(HttpStatus.UNAUTHORIZED,
			                resourceService.getDefaultLocaleMessage(SecurityMessageResourceService.UNKNOWN_TOKEN))));

		} else {
			// Need to add the basic authorisation...
		}

		return Mono.empty();
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

		return flatMapMono(

		        () -> checkTokenOrigin(request, jwtClaims),

		        claims -> this.userService.readInternal(tokenObject.getUserId()),

		        (claims, u) -> this.clientService.getClientTypeNCode(u.getClientId()),

		        (claims, u,
		                typ) -> Mono.just(new ContextAuthentication(u.toContextUser(), true,
		                        claims.getLoggedInClientId(), claims.getLoggedInClientCode(), typ.getT1(), typ.getT2(),
		                        tokenObject.getToken(), tokenObject.getExpiresAt())));
	}

	private Mono<JWTClaims> checkTokenOrigin(ServerHttpRequest request, JWTClaims jwtClaims) {

		String host = request.getURI()
		        .getHost();
		String port = "" + request.getURI()
		        .getPort();

		List<String> forwardedHost = request.getHeaders()
		        .get("X-Forwarded-Host");

		if (forwardedHost != null && !forwardedHost.isEmpty()) {
			host = forwardedHost.get(0);
		}

		List<String> forwardedPort = request.getHeaders()
		        .get("X-Forwarded-Port");
		if (forwardedPort != null && !forwardedPort.isEmpty()) {
			port = forwardedPort.get(0);
		}

		if (!host.equals(jwtClaims.getHostName()) || !port.equals(jwtClaims.getPort())) {

			return resourceService.throwMessage(HttpStatus.UNAUTHORIZED, SecurityMessageResourceService.UNKNOWN_TOKEN);
		}

		return Mono.just(jwtClaims);
	}

	public Mono<Boolean> updateNewPassword(RequestUpdatePassword requestPassword) {

		return flatMapMono(

		        SecurityContextUtil::getUsersContextAuthentication,

		        ca -> Mono.just(requestPassword.getOldPassword()
		                .equals(requestPassword.getNewPassword())
		                && requestPassword.getNewPassword()
		                        .equals(requestPassword.getConfirmPassword())),

		        (ca, passwordEqual) -> this.userService.checkPasswordEqual(ULong.valueOf(ca.getUser()
		                .getId()), requestPassword.getNewPassword()),

		        // add password policy service to verify the password when implemented

		        (ca, passwordEqual, passwordMatches) -> Mono.just(true),

		        (ca, passwordEqual, passwordMatches, isValidPasswordPolicy) ->

				{
			        if (passwordEqual.booleanValue() && passwordMatches.booleanValue()
			                && isValidPasswordPolicy.booleanValue()) {

				        Map<String, Object> updateMap = new HashMap<>();
				        updateMap.put("password", requestPassword.getNewPassword());
				        return this.userService.update(ULong.valueOf(ca.getUser()
				                .getId()), updateMap)
				                .map(Objects::nonNull);
			        }

			        return Mono.empty();

		        }

		);
	}
}
