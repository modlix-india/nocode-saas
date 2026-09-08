package com.fincity.saas.commons.core.service.connection.rest;

import com.fincity.nocode.kirun.engine.model.EventResult;
import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.core.document.Connection;
import com.fincity.saas.commons.core.dto.CoreToken;
import com.fincity.saas.commons.core.dto.RestRequest;
import com.fincity.saas.commons.core.dto.RestResponse;
import com.fincity.saas.commons.core.jooq.enums.CoreTokensTokenType;
import com.fincity.saas.commons.core.service.CoreFunctionService;
import com.fincity.saas.commons.core.service.CoreMessageResourceService;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.StringUtil;
import com.google.gson.JsonElement;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

@Service
public class RestAuthService extends AbstractRestTokenService {

    private static final String AUTH_TOKEN_FUNCTION_NAME = "authTokenFunctionName";
    private static final String AUTH_TOKEN_FUNCTION_NAMESPACE = "authTokenFunctionNamespace";

    private static final String ERROR_EVENT = "errorOutput";
    private static final String AUTH_TOKEN = "accessToken";
    private static final String EXPIRES_IN = "expiresIn";

    private static final String CACHE_NAME_REST_AUTH = "RestAuthToken";

    private CoreFunctionService coreFunctionService;

    @Autowired
    private void setCoreFunctionService(CoreFunctionService coreFunctionService) {
        this.coreFunctionService = coreFunctionService;
    }

    @Override
    public Mono<RestResponse> call(Connection connection, RestRequest request, boolean fileDownload) {
        return FlatMapUtil.flatMapMono(
                        () -> getAccessToken(connection),
                        accessToken -> makeRestCall(connection, request, accessToken, fileDownload))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "RestAuthService.call"));
    }

    private Mono<String> getAccessToken(Connection connection) {
        return FlatMapUtil.flatMapMono(
                        () -> getExistingAccessToken(connection),
                        existingAccessToken -> existingAccessToken.getT2().isAfter(LocalDateTime.now())
                                ? Mono.just(existingAccessToken.getT1())
                                : Mono.empty())
                .switchIfEmpty(Mono.defer(() -> createNewAccessToken(connection).map(Tuple2::getT1)))
                // Never complete empty. An empty Mono here silently collapses the whole REST call
                // into an empty result, which callers cannot distinguish from "not attempted".
                .switchIfEmpty(Mono.defer(() -> msgService.throwMessage(
                        msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                        CoreMessageResourceService.NOT_ABLE_TO_CREATE_TOKEN,
                        connection.getName(),
                        "no access token could be resolved or created")));
    }

    private Mono<Tuple2<String, LocalDateTime>> getExistingAccessToken(Connection connection) {
        return cacheService.cacheValueOrGet(
                CACHE_NAME_REST_AUTH,
                () -> this.coreTokenDAO.getActiveAccessTokenTuple(
                        connection.getClientCode(), connection.getAppCode(), connection.getName()),
                getCacheKeys(connection));
    }

    private Mono<Tuple2<String, LocalDateTime>> createNewAccessToken(Connection connection) {
        return FlatMapUtil.flatMapMono(
                        () -> this.executeConnectionFunction(connection),
                        authTokenOutput -> cacheService.evict(CACHE_NAME_REST_AUTH, getCacheKeys(connection)),
                        (authTokenOutput, evicted) -> authTokenOutput.getName().equals(ERROR_EVENT)
                                ? msgService.throwMessage(
                                        msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                                        CoreMessageResourceService.NOT_ABLE_TO_CREATE_TOKEN,
                                        connection.getName(),
                                        authTokenOutput)
                                : this.createCoreToken(authTokenOutput, connection))
                .map(coreCoreToken -> Tuples.of(coreCoreToken.getToken(), coreCoreToken.getExpiresAt()));
    }

    private Mono<EventResult> executeConnectionFunction(Connection connection) {
        Map<String, Object> connectionDetails = connection.getConnectionDetails();

        if (connectionDetails == null)
            return this.tokenError(connection, "connection has no connection details");

        String authTokenFunctionName = (String) connectionDetails.get(AUTH_TOKEN_FUNCTION_NAME);
        String authTokenFunctionNameSpace = (String) connectionDetails.get(AUTH_TOKEN_FUNCTION_NAMESPACE);

        if (StringUtil.safeIsBlank(authTokenFunctionName))
            return this.tokenError(connection, AUTH_TOKEN_FUNCTION_NAME + " is not configured");

        return coreFunctionService
                .execute(
                        authTokenFunctionNameSpace,
                        authTokenFunctionName,
                        connection.getAppCode(),
                        connection.getClientCode(),
                        null,
                        null)
                .flatMap(fo -> {
                    List<EventResult> results = fo.allResults();
                    if (results == null || results.isEmpty())
                        return this.tokenError(
                                connection,
                                authTokenFunctionNameSpace + "." + authTokenFunctionName + " raised no events");
                    return Mono.just(results.getFirst());
                })
                .switchIfEmpty(Mono.defer(() -> this.tokenError(
                        connection,
                        authTokenFunctionNameSpace + "." + authTokenFunctionName + " produced no output")));
    }

    private <T> Mono<T> tokenError(Connection connection, String reason) {
        return msgService.throwMessage(
                msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
                CoreMessageResourceService.NOT_ABLE_TO_CREATE_TOKEN,
                connection.getName(),
                reason);
    }

    private Mono<CoreToken> createCoreToken(EventResult authTokenOutput, Connection connection) {
        Map<String, JsonElement> eventMap = authTokenOutput.getResult();

        // An auth-token function that returns an output event without these fields used to NPE
        // here, which surfaced as an opaque failure rather than a configuration error.
        JsonElement authTokenElement = eventMap == null ? null : eventMap.get(AUTH_TOKEN);
        JsonElement expiresInElement = eventMap == null ? null : eventMap.get(EXPIRES_IN);

        if (authTokenElement == null || authTokenElement.isJsonNull())
            return this.tokenError(connection, "auth token function returned no '" + AUTH_TOKEN + "'");

        if (expiresInElement == null || expiresInElement.isJsonNull())
            return this.tokenError(connection, "auth token function returned no '" + EXPIRES_IN + "'");

        String authToken = authTokenElement.getAsString();
        long expiresIn = expiresInElement.getAsLong();

        if (StringUtil.safeIsBlank(authToken))
            return this.tokenError(connection, "auth token function returned a blank '" + AUTH_TOKEN + "'");

        return this.coreTokenDAO.create(new CoreToken()
                .setClientCode(connection.getClientCode())
                .setAppCode(connection.getAppCode())
                .setConnectionName(connection.getName())
                .setTokenType(CoreTokensTokenType.ACCESS)
                .setToken(authToken)
                .setIsRevoked(Boolean.FALSE)
                .setExpiresAt(LocalDateTime.now().plusSeconds(expiresIn)));
    }

    private Mono<RestResponse> makeRestCall(
            Connection connection, RestRequest request, String accessToken, boolean fileDownload) {
        Object tokenPrefix = connection.getConnectionDetails().get("headerPrefix");

        String authorizationHeader = (tokenPrefix != null) ? tokenPrefix + " " + accessToken : accessToken;

        MultiValueMap<String, String> headers = request.getHeaders() != null ? request.getHeaders() : new HttpHeaders();

        headers.add("Authorization", authorizationHeader);
        request.setHeaders(headers);

        return this.basicRestService.call(connection, request, fileDownload);
    }

    private Object[] getCacheKeys(Connection connection) {
        return new Object[] {connection.getClientCode(), ":", connection.getAppCode(), ":", connection.getName()};
    }
}
