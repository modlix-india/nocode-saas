package com.fincity.saas.core.service.connection.rest;

import java.time.LocalDateTime;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.mongo.service.AbstractMongoMessageResourceService;
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

@Service
public class RestAuthService extends AbstractRestService implements IRestService {

	private static final String AUTH_TOKEN_FUNCTION_NAME = "authTokenFunctionName";
	private static final String AUTH_TOKEN_FUNCTION_NAMESPACE = "authTokenFunctionNamespace";
	private static final String AUTH_TOKEN = "accessToken";
	private static final String EXPIRES_IN = "expiresIn";

	private final CoreMessageResourceService msgService;
	private final CoreTokenDAO coreTokenDAO;
	private final BasicRestService basicRestService;
	private final CoreFunctionService coreFunctionService;

	public RestAuthService(CoreMessageResourceService msgService, CoreTokenDAO coreTokenDAO,
	                       BasicRestService basicRestService, CoreFunctionService coreFunctionService) {

		this.msgService = msgService;
		this.coreTokenDAO = coreTokenDAO;
		this.basicRestService = basicRestService;
		this.coreFunctionService = coreFunctionService;

	}

	@Override
	public Mono<RestResponse> call(Connection connection, RestRequest request) {

		return FlatMapUtil.flatMapMono(

				() -> {
					Map<String, Object> connectionDetails = connection.getConnectionDetails();

					String authTokenFunctionName = (String) connectionDetails.get(AUTH_TOKEN_FUNCTION_NAME);
					String authTokenFunctionNameSpace = (String) connectionDetails.get(AUTH_TOKEN_FUNCTION_NAMESPACE);

					return coreFunctionService.execute(authTokenFunctionNameSpace, authTokenFunctionName,
							connection.getAppCode(), connection.getClientCode(), null, null);
				},

				authTokenOutput -> Mono.just(authTokenOutput.allResults().get(0))
						.switchIfEmpty(msgService.throwMessage(msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
								AbstractMongoMessageResourceService.OBJECT_NOT_FOUND, connection.getName())),

				(authTokenOutput, outputResult) -> {
					Map<String, JsonElement> eventMap = outputResult.getResult();
					String authToken = eventMap.get(AUTH_TOKEN).getAsString();
					long expiresIn = eventMap.get(EXPIRES_IN).getAsLong();

					return coreTokenDAO.create(new CoreToken()
							.setClientCode(connection.getClientCode())
							.setAppCode(connection.getAppCode())
							.setConnectionName(connection.getName())
							.setTokenType(CoreTokensTokenType.ACCESS)
							.setToken(authToken)
							.setIsRevoked(Boolean.FALSE)
							.setExpiresAt(LocalDateTime.now().plusSeconds(expiresIn)));
				},

				(authTokenOutput, outputResult, coreToken) ->
						makeRestCall(connection, request, coreToken.getToken()));
	}

	private Mono<RestResponse> makeRestCall(Connection connection, RestRequest request, String accessToken) {

		HttpHeaders headers = new HttpHeaders();

		Object tokenPrefix = connection.getConnectionDetails().get("headerPrefix");

		String authorizationHeader = (tokenPrefix != null) ? tokenPrefix + " " + accessToken : "Bearer " + accessToken;

		headers.set("Authorization", authorizationHeader);

		request.setHeaders(headers);

		return basicRestService.call(connection, request);
	}

}
