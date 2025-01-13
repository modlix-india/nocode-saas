package com.fincity.saas.core.service.connection.rest;

import java.time.LocalDateTime;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.service.CacheService;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.core.dao.CoreTokenDAO;
import com.fincity.saas.core.document.Connection;
import com.fincity.saas.core.dto.CoreToken;
import com.fincity.saas.core.dto.RestRequest;
import com.fincity.saas.core.dto.RestResponse;
import com.fincity.saas.core.jooq.enums.CoreTokensTokenType;
import com.fincity.saas.core.service.CoreFunctionService;
import com.fincity.saas.core.service.CoreMessageResourceService;
import com.google.gson.JsonElement;

import reactor.core.publisher.Mono;
import reactor.util.context.Context;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

@Service
public class RestAuthService extends AbstractRestService implements IRestService {

	private static final String AUTH_TOKEN_FUNCTION_NAME = "authTokenFunctionName";
	private static final String AUTH_TOKEN_FUNCTION_NAMESPACE = "authTokenFunctionNamespace";

	private static final String ERROR_EVENT = "errorOutput";
	private static final String AUTH_TOKEN = "accessToken";
	private static final String EXPIRES_IN = "expiresIn";

	private static final String CACHE_NAME_REST_AUTH = "RestAuthToken";

	private final CacheService cacheService;
	private final CoreMessageResourceService msgService;
	private final CoreTokenDAO coreTokenDAO;
	private final BasicRestService basicRestService;
	private final CoreFunctionService coreFunctionService;

	public RestAuthService(CoreMessageResourceService msgService, CoreTokenDAO coreTokenDAO,
			BasicRestService basicRestService, CoreFunctionService coreFunctionService, CacheService cacheService) {

		this.msgService = msgService;
		this.coreTokenDAO = coreTokenDAO;
		this.basicRestService = basicRestService;
		this.coreFunctionService = coreFunctionService;
		this.cacheService = cacheService;
	}

	@Override
	public Mono<RestResponse> call(Connection connection, RestRequest request, boolean fileDownload) {

		return FlatMapUtil.flatMapMono(
				() -> getAccessToken(connection),
				accessToken -> makeRestCall(connection, request, accessToken, fileDownload))
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "RestAuthService.call"));
	}

	@Override
	public Mono<RestResponse> call(Connection connection, RestRequest request) {

		return this.call(connection, request, false);
	}

	private Mono<String> getAccessToken(Connection connection) {

		return FlatMapUtil.flatMapMono(

				() -> getExistingAccessToken(connection),

				existingAccessToken -> existingAccessToken.getT2().isAfter(LocalDateTime.now())
						? Mono.just(existingAccessToken.getT1())
						: Mono.empty()

		).switchIfEmpty(createNewAccessToken(connection).map(Tuple2::getT1));
	}

	private Mono<Tuple2<String, LocalDateTime>> getExistingAccessToken(Connection connection) {
		return cacheService.cacheValueOrGet(CACHE_NAME_REST_AUTH, () -> this.coreTokenDAO.getActiveAccessTokenTuple(
				connection.getClientCode(), connection.getAppCode(), connection.getName()), getCacheKeys(connection));
	}

	private Mono<Tuple2<String, LocalDateTime>> createNewAccessToken(Connection connection) {

		return FlatMapUtil.flatMapMono(

				() -> {
					Map<String, Object> connectionDetails = connection.getConnectionDetails();

					String authTokenFunctionName = (String) connectionDetails.get(AUTH_TOKEN_FUNCTION_NAME);
					String authTokenFunctionNameSpace = (String) connectionDetails.get(AUTH_TOKEN_FUNCTION_NAMESPACE);

					return coreFunctionService.execute(authTokenFunctionNameSpace, authTokenFunctionName,
							connection.getAppCode(), connection.getClientCode(), null, null);
				},

				authTokenOutput -> Mono.just(authTokenOutput.allResults().get(0)),

				(authTokenOutput, outputResult) -> cacheService.evict(CACHE_NAME_REST_AUTH, getCacheKeys(connection)),

				(authTokenOutput, outputResult, removed) -> {

					if (outputResult.getName().equals(ERROR_EVENT)) {
						return msgService.throwMessage(msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
								CoreMessageResourceService.NOT_ABLE_TO_CREATE_TOKEN, connection.getName(),
								outputResult);
					}

					Map<String, JsonElement> eventMap = outputResult.getResult();
					String authToken = eventMap.get(AUTH_TOKEN).getAsString();
					long expiresIn = eventMap.get(EXPIRES_IN).getAsLong();

					return coreTokenDAO.create(new CoreToken().setClientCode(connection.getClientCode())
							.setAppCode(connection.getAppCode()).setConnectionName(connection.getName())
							.setTokenType(CoreTokensTokenType.ACCESS).setToken(authToken)
							.setIsRevoked(Boolean.FALSE).setExpiresAt(LocalDateTime.now().plusSeconds(expiresIn)));
				}).map(coreToken -> Tuples.of(coreToken.getToken(), coreToken.getExpiresAt()));
	}

	private Mono<RestResponse> makeRestCall(Connection connection, RestRequest request, String accessToken,
			boolean fileDownload) {

		Object tokenPrefix = connection.getConnectionDetails().get("headerPrefix");

		String authorizationHeader = (tokenPrefix != null) ? tokenPrefix + " " + accessToken : accessToken;

		MultiValueMap<String, String> headers = request.getHeaders() != null ? request.getHeaders() : new HttpHeaders();

		headers.add("Authorization", authorizationHeader);
		request.setHeaders(headers);

		return basicRestService.call(connection, request, fileDownload);
	}

	private Object[] getCacheKeys(Connection connection) {
		return new Object[] { connection.getClientCode(), ":", connection.getAppCode(), ":", connection.getName() };
	}

}
