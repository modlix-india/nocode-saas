package com.fincity.saas.commons.core.service.connection.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.core.document.Connection;
import com.fincity.saas.commons.core.dto.CoreToken;
import com.fincity.saas.commons.core.dto.RestRequest;
import com.fincity.saas.commons.core.dto.RestResponse;
import com.fincity.saas.commons.core.enums.ConnectionType;
import com.fincity.saas.commons.core.enums.OAuth2GrantTypes;
import com.fincity.saas.commons.core.jooq.enums.CoreTokensTokenType;
import com.fincity.saas.commons.core.service.ConnectionService;
import com.fincity.saas.commons.core.service.CoreMessageResourceService;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.jooq.util.ULongUtil;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.StringUtil;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;
import reactor.util.function.Tuple2;

@Service
public class OAuth2RestService extends AbstractRestTokenService {

    private static final String AUTH_GRANT_TYPE = "grantType";

    private static final String AUTH_HEADER_PREFIX = "authHeaderPrefix";
    private static final String HEADERS = "headers";
    private static final String TOKEN_KEY = "tokenKey";
    private static final String REFRESH_TOKEN_KEY = "refreshTokenKey";
    private static final String TOKEN_EXPIRES_AT_KEY = "expireAtKey";
    private static final String REFRESH_TOKEN_EXPIRES_AT_KEY ="refreshExpireAtKey";
    private static final String IS_LIFETIME_TOKEN = "isLifeTimeToken";

    private static final String CACHE_NAME_REST_OAUTH2 = "RestOAuthToken";

    private static final String CONSENT_CALLBACK_URI = "/api/core/connections/oauth/callback";

    private ConnectionService connectionService;

    private final WebClient webClient = WebClient.create();

    @Autowired
    private void setConnectionService(ConnectionService connectionService) {
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

        Map<String, Object> connectionDetails = connection.getConnectionDetails();
        String grantTypeString = (String) connectionDetails.get(AUTH_GRANT_TYPE);
        boolean isAuthorizationCode = OAuth2GrantTypes.AUTHORIZATION_CODE.name().equals(grantTypeString);

        return SecurityContextUtil.getUsersContextAuthentication()
                .flatMap(ca -> this.getExistingAccessToken(connection.getName(), ca.getClientCode(), ca.getUrlAppCode())
                            .flatMap(token -> {
                                if (token.getExpiresAt() == null || token.getExpiresAt().isAfter(LocalDateTime.now())) {
                                    return Mono.just(token.getToken());
                                }

                                if (isAuthorizationCode) {
                                    return refreshAccessToken(connection, ca.getClientCode(), ca.getUrlAppCode());
                                }

                                return cacheService
                                        .evict(CACHE_NAME_REST_OAUTH2,
                                                getCacheKeys(connection.getName(), token.getClientCode(), token.getAppCode()))
                                        .flatMap(v -> createClientCredentialsToken(connection));
                            })
                            .switchIfEmpty(Mono.defer(() -> {
                                if (isAuthorizationCode) {
                                    return refreshAccessToken(connection, ca.getClientCode(), ca.getUrlAppCode());
                                }
                                return createClientCredentialsToken(connection);
                            }))
                )
                .switchIfEmpty(Mono.error(new GenericException(
                        HttpStatus.BAD_REQUEST, "Access denied: Integration token unavailable or expired")));
    }

    private Mono<CoreToken> getExistingAccessToken(String connectionName, String clientCode, String appCode) {
        return cacheService.cacheValueOrGet(
                CACHE_NAME_REST_OAUTH2,
                () -> this.coreTokenDAO.getActiveToken(clientCode, appCode, connectionName, CoreTokensTokenType.ACCESS),
                getCacheKeys(connectionName, clientCode, appCode));
    }

    public Mono<String> evokeConsentAuth(String connectionName, ServerHttpRequest request) {
        String host = request.getHeaders().getFirst("X-Forwarded-Host");

        String callBackURL = "https://" + host + CONSENT_CALLBACK_URI;

        return FlatMapUtil.flatMapMono(
                SecurityContextUtil::getUsersContextAuthentication,
                ca -> connectionService.read(
                        connectionName, ca.getUrlAppCode(), ca.getUrlClientCode(), ConnectionType.REST_API),
                (ca, connection) -> {
                    String grantTypeString =
                            (String) connection.getConnectionDetails().get(AUTH_GRANT_TYPE);

                    if (!OAuth2GrantTypes.AUTHORIZATION_CODE.name().equals(grantTypeString)) {
                        return Mono.error(
                                new GenericException(HttpStatus.BAD_REQUEST, "Invalid Connection Grant Type."));
                    }

                    return getAuthConsentURI(ca, connection, callBackURL);
                });
    }

    private Mono<RestResponse> makeRestCall(
            Connection connection, RestRequest request, String accessToken, boolean fileDownload) {

        Object tokenPrefix = connection.getConnectionDetails().get(AUTH_HEADER_PREFIX);

        @SuppressWarnings("unchecked")
        Map<String, String> inputHeaders =
                (Map<String, String>) connection.getConnectionDetails().getOrDefault(HEADERS, Collections.emptyMap());

        String authorizationHeader = (tokenPrefix != null) ? tokenPrefix + " " + accessToken : accessToken;

        MultiValueMap<String, String> headers = request.getHeaders() != null ? request.getHeaders() : new HttpHeaders();

        headers.add("Authorization", authorizationHeader);

        // Add all query parameters from connection details
        inputHeaders.forEach((key, value) -> {
            if (value != null) {
                headers.add(key, value);
            }
        });

        request.setHeaders(headers);

        return basicRestService.call(connection, request, fileDownload);
    }

    private Object[] getCacheKeys(String connectionName, String clientCode, String appCode) {
        return new Object[] {connectionName, ":", clientCode, ":", appCode};
    }

    private Mono<String> getAuthConsentURI(ContextAuthentication ca, Connection connection, String callBackURL) {
        String state = UUID.randomUUID().toString();

        Map<String, Object> connectionDetails = connection.getConnectionDetails();

        @SuppressWarnings("unchecked")
        Map<String, Object> consentDetails =
                (Map<String, Object>) connectionDetails.getOrDefault("consentDetails", Collections.emptyMap());

        @SuppressWarnings("unchecked")
        Map<String, Object> queryParams =
                (Map<String, Object>) consentDetails.getOrDefault("queryParams", Collections.emptyMap());

        String authBaseURL = (String) consentDetails.getOrDefault("url", "");

        if (authBaseURL.isEmpty()) {
            return Mono.error(new GenericException(HttpStatus.BAD_REQUEST, "Authorization URL is required"));
        }

        return FlatMapUtil.flatMapMono(
                () -> this.coreTokenDAO.create(new CoreToken()
                        .setClientCode(ca.getClientCode())
                        .setAppCode(ca.getUrlAppCode())
                        .setConnectionName(connection.getName())
                        .setTokenType(CoreTokensTokenType.ACCESS)
                        .setState(state)
                        .setUserId(ULongUtil.valueOf(ca.getUser().getId()))),
                coreCoreToken -> {
                    // Build the base URI
                    UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromUriString(authBaseURL);

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

        if (request.getQueryParams().getFirst("code") == null) return invalidAuthCallback(request, response);

        String state = request.getQueryParams().getFirst("state");

        String basePageURL = "https://" + request.getHeaders().getFirst("X-Forwarded-Host");

        String callbackURI = basePageURL + request.getPath();

        return FlatMapUtil.flatMapMono(

                () -> this.coreTokenDAO.getCoreTokenByState(state),

                coreToken -> this.connectionService.read(
                        coreToken.getConnectionName(),
                        coreToken.getAppCode(),
                        coreToken.getClientCode(),
                        ConnectionType.REST_API),

                (coreToken, connection) -> this.coreTokenDAO.revokeToken(
                        coreToken.getClientCode(), coreToken.getAppCode(), coreToken.getConnectionName(), null),

                (coreToken, connection, invPrev) -> cacheService.evict(
                        CACHE_NAME_REST_OAUTH2,
                        getCacheKeys(connection.getName(), coreToken.getClientCode(), coreToken.getAppCode())),

                (coreToken, connection, invPrev, evictCache) ->
                        fetchTokenWithAuthCode(connection, request, callbackURI),

                (coreToken, connection, invPrev, evictCache, tObject) -> updateTokens(coreToken, connection, tObject),

                (coreToken, connection, invPrev, evictCache, tup, updated) -> {
                    String fullPageURI =
                            basePageURL + connection.getConnectionDetails().get("pagePath");

                    response.setStatusCode(HttpStatus.FOUND);

                    UriComponentsBuilder builder =
                            UriComponentsBuilder.fromUriString(fullPageURI).queryParam(connection.getName(), "success");

                    response.getHeaders().setLocation(URI.create(builder.build().toUriString()));

                    return Mono.empty();
                });
    }

    private Mono<Map<String, Object>> fetchTokenWithAuthCode(Connection connection, ServerHttpRequest request, String callbackURI) {

        @SuppressWarnings("unchecked")
        Map<String, Object> tokenDetails = (Map<String, Object>)
                connection.getConnectionDetails().get("tokenDetails");

        @SuppressWarnings("unchecked")
        Map<String, Object> queryParams =
                (Map<String, Object>) tokenDetails.getOrDefault("queryParams", Collections.emptyMap());

        String tokenURL = (String) tokenDetails.get("url");

        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromUriString(tokenURL);

        uriBuilder.queryParam("code", request.getQueryParams().getFirst("code"));
        uriBuilder.queryParam("redirect_uri", callbackURI);

        // Add all query parameters from connection details
        queryParams.forEach((key, value) -> {
            if (value != null) {
                uriBuilder.queryParam(key, value.toString());
            }
        });

        String methodType = (String) tokenDetails.getOrDefault("methodType", "GET");

        return (methodType.equals("POST") ? webClient.post() : webClient.get())
                .uri(uriBuilder.build().toUri())
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {});
    }

    private Mono<Tuple2<CoreToken, CoreToken>> updateTokens(CoreToken coreToken, Connection connection, Map<String, Object> tokenResponse) {

        Map<String, Object> connectionDetails = connection.getConnectionDetails();

        // Update access token
        Mono<CoreToken> accessTokenUpdate = this.coreTokenDAO.update(coreToken
                .setToken(tokenResponse.get((String) connectionDetails.get(TOKEN_KEY)).toString())
                .setExpiresAt(calculateExpiryTime(connectionDetails, tokenResponse, CoreTokensTokenType.ACCESS))
                .setIsLifetimeToken((Boolean) connectionDetails.get(IS_LIFETIME_TOKEN))
                .setTokenMetadata(tokenResponse));

        // Create refresh token if needed
        String refreshTokenKey = (String) connectionDetails.get(REFRESH_TOKEN_KEY);
        Mono<CoreToken> refreshTokenCreate = Mono.just(new CoreToken()); // Default empty token

        if (!StringUtil.safeIsBlank(refreshTokenKey) && tokenResponse.containsKey(refreshTokenKey)) {
            refreshTokenCreate = this.coreTokenDAO.create(new CoreToken()
                    .setClientCode(coreToken.getClientCode())
                    .setAppCode(connection.getAppCode())
                    .setState(coreToken.getState())
                    .setConnectionName(connection.getName())
                    .setUserId(coreToken.getUserId())
                    .setExpiresAt(calculateExpiryTime(connectionDetails, tokenResponse, CoreTokensTokenType.REFRESH))
                    .setIsLifetimeToken(calculateExpiryTime(connectionDetails, tokenResponse, CoreTokensTokenType.REFRESH) == null)
                    .setTokenType(CoreTokensTokenType.REFRESH)
                    .setToken(tokenResponse.get(refreshTokenKey).toString())
                    .setTokenMetadata(tokenResponse));
        }

        return accessTokenUpdate.zipWith(refreshTokenCreate);
    }

    private Mono<String> createClientCredentialsToken(Connection connection) {

        Map<String, Object> connectionDetails = connection.getConnectionDetails();

        return FlatMapUtil.flatMapMono(
                SecurityContextUtil::getUsersContextAuthentication,
                ca -> {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> tokenDetails = (Map<String, Object>) connectionDetails.get("tokenDetails");

                    String tokenURL = (String) tokenDetails.get("url");

                    @SuppressWarnings("unchecked")
                    Map<String, Object> queryParams = (Map<String, Object>) tokenDetails.get("queryParams");

                    UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromUriString(tokenURL);

                    queryParams.forEach((key, value) -> {
                        if (value != null) {
                            uriBuilder.queryParam(key, value.toString());
                        }
                    });

                    return webClient
                            .post()
                            .uri(uriBuilder.build().toUri())
                            .retrieve()
                            .bodyToMono(JsonNode.class);
                },
                (ca, tokenResponse) -> this.coreTokenDAO
                        .create(new CoreToken()
                                .setClientCode(connection.getClientCode())
                                .setAppCode(connection.getAppCode())
                                .setUserId(ULongUtil.valueOf(ca.getUser().getId()))
                                .setConnectionName(connection.getName())
                                .setTokenType(CoreTokensTokenType.ACCESS)
                                .setToken(tokenResponse
                                        .get((String) connectionDetails.get(TOKEN_KEY))
                                        .asText())
                                .setExpiresAt(LocalDateTime.now()
                                        .plusSeconds(Long.parseLong(tokenResponse
                                                .get((String) connectionDetails.get(TOKEN_EXPIRES_AT_KEY))
                                                .asText()))))
                        .map(CoreToken::getToken));
    }

    public Mono<Boolean> revokeConnectionToken(String connectionName) {

        return FlatMapUtil.flatMapMono(

                        SecurityContextUtil::getUsersContextAuthentication,

                        ca -> this.coreTokenDAO.getActiveToken(
                                ca.getClientCode(), ca.getUrlAppCode(), connectionName, CoreTokensTokenType.ACCESS),

                        (ca, coreToken) -> this.coreTokenDAO.revokeToken(
                                ca.getClientCode(), ca.getUrlAppCode(), connectionName, null),

                        (ca, coreToken, revoked) -> cacheService.evict(
                                CACHE_NAME_REST_OAUTH2,
                                getCacheKeys(connectionName, coreToken.getClientCode(), coreToken.getAppCode())))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "OAuth2RestService.revokeConnectionToken"))
                .switchIfEmpty(msgService.throwMessage(
                        msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                        CoreMessageResourceService.CANNOT_DELETE_TOKEN_WITH_CLIENT_CODE,
                        connectionName));
    }

    public Mono<String> getAccessToken(String connectionName) {
        return FlatMapUtil.flatMapMono(SecurityContextUtil::getUsersContextAuthentication, ca -> connectionService
                .read(connectionName, ca.getUrlAppCode(), ca.getClientCode(), ConnectionType.REST_API)
                .flatMap(this::getAccessToken));
    }

    private Mono<Void> invalidAuthCallback(ServerHttpRequest request, ServerHttpResponse response) {
        String state = request.getQueryParams().getFirst("state");

        String basePageURL = "https://" + request.getHeaders().getFirst("X-Forwarded-Host");

        response.setStatusCode(HttpStatus.FOUND);

        return FlatMapUtil.flatMapMono(
                () -> this.coreTokenDAO.getCoreTokenByState(state),
                coreCoreToken -> this.connectionService.read(
                        coreCoreToken.getConnectionName(),
                        coreCoreToken.getAppCode(),
                        coreCoreToken.getClientCode(),
                        ConnectionType.REST_API),
                (coreCoreToken, connection) -> {
                    response.setStatusCode(HttpStatus.FOUND);

                    UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(basePageURL
                                    + connection.getConnectionDetails().get("pagePath"))
                            .queryParam(connection.getName(), "invalid");

                    response.getHeaders().setLocation(URI.create(builder.build().toUriString()));

                    return Mono.empty();
                });
    }

    private LocalDateTime calculateExpiryTime(
            Map<String, Object> connectionDetails, Map<String, Object> tokenResponse, CoreTokensTokenType tokenType) {

        boolean isLifetimeToken = Boolean.TRUE.equals(connectionDetails.getOrDefault(IS_LIFETIME_TOKEN, Boolean.FALSE));

        String expiresAtKey = (String) connectionDetails.get(
                tokenType.equals(CoreTokensTokenType.ACCESS) ? TOKEN_EXPIRES_AT_KEY : REFRESH_TOKEN_EXPIRES_AT_KEY);

        if (isLifetimeToken || StringUtil.safeIsBlank(tokenResponse.get(expiresAtKey))) {
            return null;
        }

        long expiresInSeconds = Long.parseLong(tokenResponse.get(expiresAtKey).toString());
        return LocalDateTime.now().plusSeconds(expiresInSeconds);
    }

    private Mono<String> refreshAccessToken(Connection connection, String clientCode, String appCode) {

        Map<String, Object> connectionDetails = connection.getConnectionDetails();

        return FlatMapUtil.flatMapMono(

                () -> this.coreTokenDAO.revokeToken(clientCode, appCode, connection.getName(), CoreTokensTokenType.ACCESS),

                revoked -> this.coreTokenDAO.getActiveToken(clientCode, appCode, connection.getName(), CoreTokensTokenType.REFRESH),

                (revoked, refreshToken) -> fetchTokenWithRefreshToken(refreshToken.getToken(), connectionDetails),

                (revoked,refreshToken, tokenResponse) -> this.coreTokenDAO.create(new CoreToken()
                        .setClientCode(refreshToken.getClientCode())
                        .setAppCode(refreshToken.getAppCode())
                        .setState(refreshToken.getState())
                        .setConnectionName(refreshToken.getConnectionName())
                        .setUserId(refreshToken.getUserId())
                        .setTokenType(CoreTokensTokenType.ACCESS)
                        .setToken(tokenResponse.get((String) connectionDetails.get(TOKEN_KEY)).toString())
                        .setExpiresAt(calculateExpiryTime(connectionDetails, tokenResponse, CoreTokensTokenType.ACCESS))
                        .setIsLifetimeToken((Boolean) connectionDetails.get(IS_LIFETIME_TOKEN))
                        .setTokenMetadata(tokenResponse)),

                (revoked, refreshToken, tokenResponse, updatedToken) -> cacheService.evict(
                                                            CACHE_NAME_REST_OAUTH2,
                                                            getCacheKeys(connection.getName(), clientCode, appCode)),

                (revoked, refreshToken, tokenResponse, updatedToken, evictCache) ->
                        Mono.just(updatedToken.getToken()))
                .switchIfEmpty(Mono.defer(() -> Mono.error(new GenericException(
                        HttpStatus.BAD_REQUEST, "Access denied: Integration token unavailable or expired"))));
    }

    private Mono<Map<String, Object>> fetchTokenWithRefreshToken(String refreshToken, Map<String, Object> connectionDetails) {

        @SuppressWarnings("unchecked")
        Map<String, Object> updateToken = (Map<String, Object>) connectionDetails.get("updateToken");

        @SuppressWarnings("unchecked")
        Map<String, String> queryParams = (Map<String, String>) updateToken.getOrDefault("queryParams", Collections.emptyMap());

        String tokenURL = (String) updateToken.get("url");

        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromUriString(tokenURL);

        uriBuilder.queryParam("refresh_token", refreshToken);

        queryParams.forEach((key, value) -> {
            if (value != null) {
                uriBuilder.queryParam(key, value);
            }
        });

        String methodType = (String) updateToken.getOrDefault("methodType", "POST");

        return (methodType.equals("POST") ? webClient.post() : webClient.get())
                .uri(uriBuilder.build().toUri())
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {});
    }
}
