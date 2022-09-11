package com.fincity.security.service;

import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDateTime;

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

import static com.fincity.reactor.util.FlatMapUtil.flatMapMono;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.commons.model.condition.FilterConditionOperator;
import com.fincity.saas.commons.model.service.CacheService;
import com.fincity.security.dto.Client;
import com.fincity.security.dto.ClientPasswordPolicy;
import com.fincity.security.dto.SoxLog;
import com.fincity.security.dto.TokenObject;
import com.fincity.security.dto.User;
import com.fincity.security.jooq.enums.SecuritySoxLogActionName;
import com.fincity.security.jooq.enums.SecuritySoxLogObjectName;
import com.fincity.security.jwt.ContextAuthentication;
import com.fincity.security.jwt.JWTClaims;
import com.fincity.security.jwt.JWTUtil;
import com.fincity.security.model.AuthenticationRequest;
import com.fincity.security.model.AuthenticationResponse;

import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;

@Service
public class AuthenticationService {

	@Autowired
	private UserService userService;

	@Autowired
	private ClientService clientService;

	@Autowired
	private TokenService tokenService;

	@Autowired
	private MessageResourceService resourceService;

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

	private static final String CACHE_NAME_TOKEN = "tokenCache";

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

		return flatMapMono(

		        () -> this.clientService.getClientId(request),

		        clientId -> userService.findByClientIdsUserName(clientId, authRequest.getUserName(),
		                authRequest.getIdentifierType()),

		        (clientId, u) -> this.clientService.readInternal(u.getClientId()),

		        (clientId, u, c) -> this.checkPassword(authRequest, u),

		        (clientId, u, c, o) -> clientService.getClientPasswordPolicy(c.getId()),

		        (clientId, u, c, o, pol) -> this.checkFailedAttempts(u, pol),

		        (clientId, u, c, o, pol, j) ->
				{

			        userService.resetFailedAttempt(u.getId())
			                .subscribe();

			        soxLogService.create(new SoxLog().setObjectId(u.getId())
			                .setActionName(SecuritySoxLogActionName.LOGIN)
			                .setObjectName(SecuritySoxLogObjectName.USER)
			                .setDescription("Successful"))
			                .subscribe();

			        URI uri = request.getURI();

			        InetSocketAddress inetAddress = request.getRemoteAddress();
			        final String setAddress = inetAddress == null ? null : inetAddress.getHostString();

			        return makeToken(authRequest, response, uri, setAddress, u, c, clientId);
		        }).switchIfEmpty(Mono.defer(this::credentialError));
	}

	private Mono<AuthenticationResponse> makeToken(AuthenticationRequest authRequest, ServerHttpResponse response,
	        URI uri, final String setAddress, User u, Client c, ULong cid) {

		int timeInMinutes = authRequest.isRememberMe() ? remembermeExpiryInMinutes : c.getTokenValidityMinutes();
		if (timeInMinutes <= 0)
			timeInMinutes = this.defaultExpiryInMinutes;

		Tuple2<String, LocalDateTime> token = JWTUtil.generateToken(u.getId()
		        .toBigInteger(), tokenKey, timeInMinutes, uri.getHost(), uri.getPort(), cid.toBigInteger());

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
		        .map(t -> new AuthenticationResponse().setUser(u)
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

	private Mono<Object> checkPassword(AuthenticationRequest authRequest, User u) {

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
				        .map(e -> 1);

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
				        .map(e -> 1);
			}
		}

		return Mono.just(1);
	}

	private Mono<? extends AuthenticationResponse> credentialError() {

		return resourceService.getMessage(MessageResourceService.USER_CREDENTIALS_MISMATCHED)
		        .map(msg ->
				{
			        throw new GenericException(HttpStatus.FORBIDDEN, msg);
		        });

	}

	public Mono<Authentication> getAuthentication(boolean basic, String bearerToken, ServerHttpRequest request) {

		Mono<Authentication> auth = cacheService.get(CACHE_NAME_TOKEN, bearerToken);
		return auth.switchIfEmpty(Mono.defer(() -> getAuthenticationIfNotInCache(basic, bearerToken, request)));
	}

	private Mono<Authentication> getAuthenticationIfNotInCache(boolean basic, String bearerToken,
	        ServerHttpRequest request) {

		if (!basic) {

			JWTClaims c;
			try {
				c = JWTUtil.getClaimsFromToken(this.tokenKey, bearerToken);
			} catch (Exception ex) {
				throw new GenericException(HttpStatus.UNAUTHORIZED,
				        resourceService.getDefaultLocaleMessage(MessageResourceService.TOKEN_EXPIRED), ex);
			}

			final var claims = c;

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
			                resourceService.getDefaultLocaleMessage(MessageResourceService.UNKNOWN_TOKEN))));

		} else {
			// Need to add the basic authorisation...
		}

		return Mono.empty();
	}

	private String toPartToken(String bearerToken) {
		return bearerToken.length() > 50 ? bearerToken.substring(bearerToken.length() - 50) : bearerToken;
	}

	private Mono<ContextAuthentication> makeSpringAuthentication(ServerHttpRequest request, JWTClaims jwtClaims,
	        TokenObject tokenObject) {

		URI uri = request.getURI();
		if (!uri.getHost()
		        .equals(jwtClaims.getHostName()) || uri.getPort() != jwtClaims.getPort()) {
			return Mono.error(new GenericException(HttpStatus.UNAUTHORIZED,
			        resourceService.getDefaultLocaleMessage(MessageResourceService.UNKNOWN_TOKEN)));
		}

		return this.userService.readInternal(tokenObject.getUserId())
		        .flatMap(u -> this.clientService.getClientType(u.getClientId())
		                .map(typ -> new ContextAuthentication(u.toContextUser(), true, jwtClaims.getLoggedInClientId(),
		                        typ)));
	}
}
