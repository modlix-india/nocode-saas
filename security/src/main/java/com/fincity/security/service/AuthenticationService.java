package com.fincity.security.service;

import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Duration;
import java.time.LocalDateTime;

import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.fincity.security.dto.Client;
import com.fincity.security.dto.ClientPasswordPolicy;
import com.fincity.security.dto.SoxLog;
import com.fincity.security.dto.TokenObject;
import com.fincity.security.dto.User;
import com.fincity.security.exception.GenericException;
import com.fincity.security.jooq.enums.SecuritySoxLogActionName;
import com.fincity.security.jooq.enums.SecuritySoxLogObjectName;
import com.fincity.security.jwt.ContextAuthentication;
import com.fincity.security.jwt.JWTClaims;
import com.fincity.security.jwt.JWTUtil;
import com.fincity.security.model.AuthenticationRequest;
import com.fincity.security.model.AuthenticationResponse;
import com.fincity.security.model.condition.FilterCondition;
import com.fincity.security.model.condition.FilterConditionOperator;

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

	public Mono<AuthenticationResponse> authenticate(AuthenticationRequest authRequest, ServerHttpRequest request,
	        ServerHttpResponse response) {

		Mono<ULong> clientId = this.clientService.getClientId(request);

		Mono<User> user = clientId.flatMap(clients -> userService.findByClientIdsUserName(clients,
		        authRequest.getUserName(), authRequest.getIdentifierType()));

		Mono<Client> client = user.flatMap(u -> this.clientService.read(u.getClientId()));

		URI uri = request.getURI();

		InetSocketAddress inetAddress = request.getRemoteAddress();
		final String setAddress = inetAddress == null ? null : inetAddress.getHostString();

		return user.flatMap(u -> checkPassword(authRequest, u).flatMap(e ->

		client.flatMap(c -> clientService.getClientPasswordPolicy(c.getId())
		        .flatMap(pol -> checkFailedAttempts(u, pol).flatMap(s ->
				{
			        userService.resetFailedAttempt(u.getId())
			                .subscribe();

			        soxLogService.create(new SoxLog().setObjectId(u.getId())
			                .setActionName(SecuritySoxLogActionName.LOGIN)
			                .setObjectName(SecuritySoxLogObjectName.USER)
			                .setDescription("Successful"))
			                .subscribe();

			        return clientId.flatMap(cid -> makeToken(authRequest, response, uri, setAddress, u, c, cid));

		        })))))
		        .switchIfEmpty(Mono.defer(this::credentialError));
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
			if (!pwdEncoder.matches(u.getFirstName() + authRequest.getPassword(), u.getPassword())) {

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

		return resourceService.getMessage("user_credentials_mismatched")
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
				        resourceService.getDefaultLocaleMessage("token_expired"), ex);
			}

			final var claims = c;

			return tokenService.readAllFilter(new FilterCondition().setField("partToken")
			        .setOperator(FilterConditionOperator.EQUALS)
			        .setValue(
			                bearerToken.length() > 50 ? bearerToken.substring(bearerToken.length() - 50) : bearerToken))
			        .filter(e -> e.getToken()
			                .equals(bearerToken))
			        .take(1)
			        .next()
			        .flatMap(e -> this.makeSpringAuthentication(request, claims, e))
			        .map(e ->
					{
				        cacheService.put(CACHE_NAME_TOKEN, e, bearerToken);
				        return (Authentication) e;
			        })
			        .switchIfEmpty(Mono.error(new GenericException(HttpStatus.UNAUTHORIZED,
			                resourceService.getDefaultLocaleMessage("unknown_token"))));

		} else {
			// Need to add the basic authorisation...
		}

		return Mono.empty();
	}

	private Mono<ContextAuthentication> makeSpringAuthentication(ServerHttpRequest request, JWTClaims jwtClaims,
	        TokenObject tokenObject) {

		URI uri = request.getURI();
		if (!uri.getHost()
		        .equals(jwtClaims.getHostName()) || uri.getPort() != jwtClaims.getPort()) {
			return Mono.error(new GenericException(HttpStatus.UNAUTHORIZED,
			        resourceService.getDefaultLocaleMessage("unknown_token")));
		}

		return this.userService.read(tokenObject.getUserId())
		        .flatMap(u -> this.clientService.getClientType(u.getClientId())
		                .map(typ -> new ContextAuthentication(u.toContextUser(), true, jwtClaims.getLoggedInClientId(),
		                        typ)));
	}
}
