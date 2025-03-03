package com.fincity.saas.core.service.connection.rest;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.jooq.util.ULongUtil;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.service.CacheService;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.saas.core.dao.CoreTokenDAO;
import com.fincity.saas.core.document.Connection;
import com.fincity.saas.core.dto.CoreToken;
import com.fincity.saas.core.dto.RestRequest;
import com.fincity.saas.core.dto.RestResponse;
import com.fincity.saas.core.enums.ConnectionType;
import com.fincity.saas.core.enums.OAuth2GrantTypes;
import com.fincity.saas.core.jooq.enums.CoreTokensTokenType;
import com.fincity.saas.core.service.ConnectionService;
import com.fincity.saas.core.service.CoreMessageResourceService;

import reactor.core.publisher.Mono;
import reactor.util.context.Context;
import reactor.util.function.Tuples;

@Service
public class OAuth2RestService extends AbstractRestService implements IRestService {

	private static final String AUTH_GRANT_TYPE = "grantType";

	private static final String AUTH_HEADER_PREFIX = "authHeaderPrefix";
	private static final String TOKEN_KEY = "tokenKey";
	private static final String REFRESH_TOKEN_KEY = "refreshTokenKey";
	private static final String TOKEN_EXPIRES_AT_KEY = "expireAtKey";
	private static final String IS_LIFETIME_TOKEN = "isLifeTimeToken";

	private static final String CACHE_NAME_REST_OAUTH2 = "RestOAuthToken";

	private static final String CONSENT_CALLBACK_URI = "/api/core/connections/oauth/callback";

	private final CacheService cacheService;
	private final CoreMessageResourceService msgService;
	private final CoreTokenDAO coreTokenDAO;
	private final BasicRestService basicRestService;
	private final ConnectionService connectionService;

	public OAuth2RestService(CoreMessageResourceService msgService, CoreTokenDAO coreTokenDAO,
			BasicRestService basicRestService, CacheService cacheService, ConnectionService connectionService) {

		this.msgService = msgService;
		this.coreTokenDAO = coreTokenDAO;
		this.basicRestService = basicRestService;
		this.cacheService = cacheService;
		this.connectionService = connectionService;
	}

	@Override
	public Mono<RestResponse> call(Connection connection, RestRequest request, boolean fileDownload) {

		return FlatMapUtil.flatMapMonoWithNull(

				() -> getAccessToken(connection),

				token -> makeRestCall(connection, request, token, fileDownload))
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "RestAuthService.call"));
	}

	@Override
	public Mono<RestResponse> call(Connection connection, RestRequest request) {

		return this.call(connection, request, false);
	}

	private Mono<String> getAccessToken(Connection connection) {

		return FlatMapUtil.flatMapMonoWithNull(

				SecurityContextUtil::getUsersContextAuthentication,

				ca -> {
					if(!ca.isAuthenticated()){
						return this.msgService.throwMessage(
								msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
								CoreMessageResourceService.FORBIDDEN_EXECUTION, connection.getName());
					}
					return this.getExistingAccessToken(connection.getName(), ca.getClientCode(), ca.getUrlAppCode());
				},

				(ca,token) -> {
					if (token == null) {

						String grantTypeString = (String) connection.getConnectionDetails().get(AUTH_GRANT_TYPE);

						if (OAuth2GrantTypes.AUTHORIZATION_CODE.name().equals(grantTypeString)) {
							return Mono.error(new GenericException(HttpStatus.BAD_REQUEST,
									"Access denied: Integration token unavailable or expired"));
						}

						return createClientCredentialsToken(connection);
					}

					return Mono.just(token);
				});
	}

	private Mono<String> getExistingAccessToken(String connectionName, String clientCode, String appCode) {
		return cacheService.cacheValueOrGet(CACHE_NAME_REST_OAUTH2, () -> this.coreTokenDAO.getActiveAccessToken(
				clientCode, appCode, connectionName), getCacheKeys(connectionName, clientCode, appCode));
	}

	public Mono<String> evokeConsentAuth(String connectionName, ServerHttpRequest request) {

		String host = request.getHeaders().getFirst("X-Forwarded-Host");

		String callBackURL = "https://" + host + CONSENT_CALLBACK_URI;

		return FlatMapUtil.flatMapMono(

				SecurityContextUtil::getUsersContextAuthentication,

				ca -> connectionService
						.read(connectionName, ca.getUrlAppCode(), ca.getUrlClientCode(), ConnectionType.REST_API),

				(ca, connection) -> {

					String grantTypeString = (String) connection.getConnectionDetails().get(AUTH_GRANT_TYPE);

					if (!OAuth2GrantTypes.AUTHORIZATION_CODE.name().equals(grantTypeString)) {
						return Mono.error(new GenericException(HttpStatus.BAD_REQUEST,
								"Invalid Connection Grant Type."));
					}

					return getAuthConsentURI(ca, connection, callBackURL);
				});
	}

	private Mono<RestResponse> makeRestCall(Connection connection, RestRequest request, String accessToken,
			boolean fileDownload) {

		Object tokenPrefix = connection.getConnectionDetails().get(AUTH_HEADER_PREFIX);

		String authorizationHeader = (tokenPrefix != null) ? tokenPrefix + " " + accessToken : accessToken;

		MultiValueMap<String, String> headers = request.getHeaders() != null ? request.getHeaders() : new HttpHeaders();

		headers.add("Authorization", authorizationHeader);
		request.setHeaders(headers);

		return basicRestService.call(connection, request, fileDownload);
	}

	private Object[] getCacheKeys(String connectionName, String clientCode, String appCode) {
		return new Object[] { connectionName, ":", clientCode, ":", appCode };
	}

	private Mono<String> getAuthConsentURI(ContextAuthentication ca, Connection connection, String callBackURL) {

		String state = UUID.randomUUID().toString();

		Map<String, Object> connectionDetails = connection.getConnectionDetails();

		@SuppressWarnings("unchecked")
		Map<String, Object> consentDetails = (Map<String, Object>) connectionDetails.getOrDefault("consentDetails",
				Collections.emptyMap());

		@SuppressWarnings("unchecked")
		Map<String, Object> queryParams = (Map<String, Object>) consentDetails.getOrDefault("queryParams",
				Collections.emptyMap());

		String authBaseURL = (String) consentDetails.getOrDefault("url", "");

		if (authBaseURL.isEmpty()) {
			return Mono.error(new GenericException(HttpStatus.BAD_REQUEST,
					"Authorization URL is required"));
		}

		return FlatMapUtil.flatMapMono(

				() -> coreTokenDAO.create(new CoreToken()
						.setClientCode(ca.getClientCode())
						.setAppCode(ca.getUrlAppCode())
						.setConnectionName(connection.getName())
						.setTokenType(CoreTokensTokenType.ACCESS).setState(state)
						.setUserId(ULongUtil.valueOf(ca.getUser().getId()))),

				coreToken -> {

					// Build the base URI
					UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromHttpUrl(authBaseURL);

					// Add all query parameters from connectionDetails
					queryParams.forEach((key, value) -> {
						if (value != null) {
							uriBuilder.queryParam(key, value.toString());
						}
					});

					uriBuilder.queryParam("redirect_uri", callBackURL);

					uriBuilder.queryParam("state", state);

					URI authUri = uriBuilder.build().toUri();

					return Mono.just(authUri.toString());

				});
	}

	public Mono<Void> oAuth2Callback(ServerHttpRequest request, ServerHttpResponse response) {

		if(request.getQueryParams().getFirst("code") ==null)
			return invalidAuthCallback(request, response);

		String state = request.getQueryParams().getFirst("state");

		String basePageURL = "https://" + request.getHeaders().getFirst("X-Forwarded-Host");

		WebClient webClient = WebClient.create();

		String callbackURI = "https://" + request.getHeaders().getFirst("X-Forwarded-Host")
				+ request.getPath();

		return FlatMapUtil.flatMapMono(

				() -> this.coreTokenDAO.getCoreTokenByState(state),

				coreToken -> this.connectionService.read(coreToken.getConnectionName(), coreToken.getAppCode(),
						coreToken.getClientCode(), ConnectionType.REST_API),

				(coreToken, connection) -> this.coreTokenDAO.revokeToken(coreToken.getClientCode(),
						coreToken.getAppCode(), coreToken.getConnectionName()),

				(coreToken, connection, invPrev) -> cacheService.evict(CACHE_NAME_REST_OAUTH2,
						getCacheKeys(connection.getName(), coreToken.getClientCode(), coreToken.getAppCode())),

				(coreToken, connection, invPrev, evictCache) -> {

					String fullPageURI = basePageURL + (String) connection.getConnectionDetails().get("pagePath");

					@SuppressWarnings("unchecked")
					Map<String, Object> tokenDetails = (Map<String, Object>) connection.getConnectionDetails()
							.get("tokenDetails");

					@SuppressWarnings("unchecked")
					Map<String, Object> queryParams = (Map<String, Object>) tokenDetails.getOrDefault("queryParams",
							Collections.emptyMap());

					String tokenURL = (String) tokenDetails.get("url");

					UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromHttpUrl(tokenURL);

					uriBuilder.queryParam("code", request.getQueryParams().getFirst("code"));
					uriBuilder.queryParam("redirect_uri", callbackURI);

					// Add all query parameters from connection details
					queryParams.forEach((key, value) -> {
						if (value != null) {
							uriBuilder.queryParam(key, value.toString());
						}
					});

					return webClient.get()
							.uri(uriBuilder.build().toUri())
							.retrieve()
							.bodyToMono(JsonNode.class)
							.map(node -> Tuples.of(fullPageURI, node));
				},

				(coreToken, connection, invPrev, evictCache, tup) -> {

					Map<String, Object> connectionDetails = connection.getConnectionDetails();

					Mono<CoreToken> token = this.coreTokenDAO.update(
							coreToken.setToken(
											tup.getT2().get((String) connectionDetails.get(TOKEN_KEY)).asText())
									.setExpiresAt(
											Boolean.FALSE.equals(connectionDetails.get(IS_LIFETIME_TOKEN))
													|| !StringUtil.safeIsBlank(tup.getT2()
													.get((String) connectionDetails.get(TOKEN_EXPIRES_AT_KEY)))
													? LocalDateTime.now().plusSeconds(Long
													.parseLong(tup.getT2()
															.get((String) connectionDetails.get(TOKEN_EXPIRES_AT_KEY))
															.asText())
											)
													: null
									)
									.setIsLifetimeToken((Boolean) connectionDetails.get(IS_LIFETIME_TOKEN))
									.setTokenMetadata(tup.getT2()));

					String rTokenKey = (String) connectionDetails.get(REFRESH_TOKEN_KEY);

					// saving refresh token if present
					if (!StringUtil.safeIsBlank(rTokenKey))
						this.coreTokenDAO.create(new CoreToken()
								.setClientCode(connection.getClientCode())
								.setAppCode(connection.getAppCode())
								.setConnectionName(connection.getName())
								.setUserId(coreToken.getUserId())
								.setExpiresAt(coreToken.getExpiresAt())
								.setTokenType(CoreTokensTokenType.REFRESH)
								.setToken(tup.getT2().get(rTokenKey).asText()));

					return token;
				},

				(coreToken, connection, invPrev, evictCache, tup, updated) -> {
					response.setStatusCode(HttpStatus.FOUND);

					UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(tup.getT1())
							.queryParam("connection", "success");

					response.getHeaders()
							.setLocation(URI.create(builder.build().toUriString()));

					return Mono.empty();
				});
	}

	private Mono<String> createClientCredentialsToken(Connection connection) {

		WebClient webClient = WebClient.create();

		Map<String, Object> connectionDetails = connection.getConnectionDetails();

		return FlatMapUtil.flatMapMono(

				SecurityContextUtil::getUsersContextAuthentication,

				ca -> {

					@SuppressWarnings("unchecked")
					Map<String, Object> tokenDetails = (Map<String, Object>) connectionDetails.get("tokenDetails");

					String tokenURL = (String) tokenDetails.get("url");

					@SuppressWarnings("unchecked")
					Map<String, Object> queryParams = (Map<String, Object>) tokenDetails.get("queryParams");

					UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromHttpUrl(tokenURL);

					queryParams.forEach((key, value) -> {
						if (value != null) {
							uriBuilder.queryParam(key, value.toString());
						}
					});

					return webClient.post()
							.uri(uriBuilder.build().toUri())
							.retrieve()
							.bodyToMono(JsonNode.class);
				},

				(ca, tokenResponse) -> this.coreTokenDAO.create(new CoreToken()
						.setClientCode(connection.getClientCode())
						.setAppCode(connection.getAppCode())
						.setUserId(ULongUtil.valueOf(ca.getUser().getId()))
						.setConnectionName(connection.getName())
						.setTokenType(CoreTokensTokenType.ACCESS)
						.setToken(tokenResponse.get((String) connectionDetails.get(TOKEN_KEY)).asText())
						.setExpiresAt(LocalDateTime.now()
								.plusSeconds(Long.parseLong(tokenResponse
										.get((String) connectionDetails.get(TOKEN_EXPIRES_AT_KEY)).asText()))))
						.map(CoreToken::getToken));

	}

	public Mono<Boolean> revokeConnectionToken(String connectionName) {

		return FlatMapUtil.flatMapMono(

				SecurityContextUtil::getUsersContextAuthentication,

				ca -> Mono.just(Tuples.of(ca.getClientCode(), ca.getUrlAppCode())),

				(ca, tup) -> this.coreTokenDAO.revokeToken(tup.getT1(), tup.getT2(), connectionName)

		).contextWrite(Context.of(LogUtil.METHOD_NAME, "OAuth2RestService.revokeConnectionToken"))
				.switchIfEmpty(msgService.throwMessage(msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
						CoreMessageResourceService.CANNOT_DELETE_TOKEN_WITH_CLIENT_CODE, connectionName));
	}

	public Mono<String> getAccessToken(String connectionName) {

		return FlatMapUtil.flatMapMono(

				SecurityContextUtil::getUsersContextAuthentication,

				ca -> connectionService.read(connectionName, ca.getUrlAppCode(), ca.getClientCode(), ConnectionType.REST_API)
						.flatMap(this::getAccessToken)

		);

	}

	private Mono<Void> invalidAuthCallback(ServerHttpRequest request, ServerHttpResponse response) {

		String state = request.getQueryParams().getFirst("state");

		String basePageURL = "https://" + request.getHeaders().getFirst("X-Forwarded-Host");

		response.setStatusCode(HttpStatus.FOUND);

		return FlatMapUtil.flatMapMono(

				() -> this.coreTokenDAO.getCoreTokenByState(state),

				coreToken -> this.connectionService.read(coreToken.getConnectionName(), coreToken.getAppCode(),
						coreToken.getClientCode(), ConnectionType.REST_API),

				(coreToken, connection) -> {

					response.setStatusCode(HttpStatus.FOUND);

					UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(basePageURL + connection.getConnectionDetails().get("pagePath"))
							.queryParam("connection", "invalid");

					response.getHeaders()
							.setLocation(URI.create(builder.build().toUriString()));

					return Mono.empty();

				}
		);
	}

}
