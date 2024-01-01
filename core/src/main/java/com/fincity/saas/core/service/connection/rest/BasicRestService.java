package com.fincity.saas.core.service.connection.rest;

import java.time.Duration;
import java.util.Map;

import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriBuilder;

import com.fincity.saas.core.document.Connection;
import com.fincity.saas.core.dto.RestResponse;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import reactor.core.publisher.Mono;

@Service
public class BasicRestService extends AbstractRestService implements IRestService {

	@Override
	public Mono<RestResponse> call(String url, MultiValueMap<String, String> headers, String[] pathParameters,
			Map<String, String> queryParameters, int timeout, Connection connection, String method,
			JsonElement payload) {

		WebClient.Builder webClientBuilder = WebClient.builder();
		Duration timeoutDuration = Duration.ofSeconds(timeout == 0 ? 300 : timeout);

		if (connection != null) {
			webClientBuilder = applyConnectionDetails(webClientBuilder, connection);
			if (connection.getConnectionDetails().containsKey("baseUrl")) {
				String baseUrl = connection.getConnectionDetails().get("baseUrl").toString();
				if (baseUrl.trim().endsWith("/"))
					baseUrl = baseUrl.substring(0, baseUrl.lastIndexOf('/'));
				if (url.trim().startsWith("/")) {
					url = url.substring(1, url.length());
				}
				url = baseUrl + '/' + url;
			}
		}
		Gson gson = new Gson();
		WebClient webClient = webClientBuilder.baseUrl(url).build();
		WebClient.RequestBodySpec  requestBuilder = webClient.method(HttpMethod.resolve(method)).uri(uriBuilder -> {
			uriBuilder = applyPathParameters(uriBuilder, pathParameters);
			uriBuilder = applyQueryParameters(uriBuilder, queryParameters);
			return uriBuilder.build();
		}).headers(httpHeaders -> applyHeaders(httpHeaders, headers));
		if(payload != null) {
			return requestBuilder.bodyValue(gson.fromJson(payload, Object.class))
				.exchangeToMono(clientResponse -> handleResponse(clientResponse, timeoutDuration));
		}
		return requestBuilder.exchangeToMono(clientResponse -> handleResponse(clientResponse, timeoutDuration));
	}

	private WebClient.Builder applyConnectionDetails(WebClient.Builder builder, Connection connection) {

		Map<String, Object> connectionDetails = connection.getConnectionDetails();
		if (connectionDetails.containsKey("userName") && connectionDetails.containsKey("password")) {
			String userName = (String) connectionDetails.get("userName");
			String password = (String) connectionDetails.get("password");
			String basicAuth = "Basic "
					+ java.util.Base64.getEncoder().encodeToString((userName + ":" + password).getBytes());
			return builder.defaultHeader("Authorization", basicAuth);
		}
		return builder;
	}

	private UriBuilder applyPathParameters(UriBuilder uriBuilder, String[] pathParameters) {
		if (pathParameters != null && pathParameters.length > 0) {
			return uriBuilder.pathSegment(pathParameters);
		}
		return uriBuilder;

	}

	private HttpHeaders applyHeaders(HttpHeaders headers, MultiValueMap<String, String> givenHeaders) {
		if (givenHeaders == null || givenHeaders.isEmpty())
			return headers;
		headers.addAll(givenHeaders);
		return headers;
	}

	private UriBuilder applyQueryParameters(UriBuilder uriBuilder, Map<String, String> queryParameters) {
		if (queryParameters != null && !queryParameters.isEmpty()) {
			for (Map.Entry<String, String> entry : queryParameters.entrySet()) {
				uriBuilder.queryParam(entry.getKey(), entry.getValue());
			}
		}
		return uriBuilder;
	}

	private Mono<RestResponse> handleResponse(ClientResponse clientResponse, Duration timeout) {
		HttpHeaders headers = clientResponse.headers().asHttpHeaders();
		MediaType contentType = headers.getContentType();

		if (contentType != null) {
			RestResponse restResponse = new RestResponse();
			restResponse.setHeaders(clientResponse.headers().asHttpHeaders().toSingleValueMap());
			restResponse.setStatus(clientResponse.statusCode().value());

			if (contentType.isCompatibleWith(MediaType.APPLICATION_JSON)) {

				return clientResponse.bodyToMono(String.class)
						.map(jsonData -> processJsonResponse(jsonData, restResponse)).timeout(timeout)
						.onErrorResume(throwable -> Mono.just(createErrorResponse(throwable)));
			} else if (contentType.isCompatibleWith(MediaType.TEXT_PLAIN)) {

				return clientResponse.bodyToMono(String.class)
						.map(textData -> processTextResponse(textData, restResponse)).timeout(timeout)
						.onErrorResume(throwable -> Mono.just(createErrorResponse(throwable)));
			} else if (contentType.getType().equals(MimeTypeUtils.APPLICATION_OCTET_STREAM.getType())) {
				return clientResponse.bodyToMono(Resource.class)
						.map(binaryData -> processBinaryResponse(binaryData, restResponse)).timeout(timeout)
						.onErrorResume(throwable -> Mono.just(createErrorResponse(throwable)));
			} else {
				return Mono.just(createErrorResponse(new UnsupportedOperationException("Unsupported content type")));
			}
		} else {
			return Mono.just(createErrorResponse(new IllegalStateException("Content-Type header not present")));
		}
	}

	private RestResponse processJsonResponse(String jsonData, RestResponse restResponse) {
		restResponse.setData(JsonParser.parseString(jsonData));
		restResponse.setStatus(HttpStatus.OK.value());
		return restResponse;
	}

	private RestResponse processTextResponse(String textData, RestResponse restResponse) {
		restResponse.setData(textData);
		return restResponse;
	}

	private RestResponse processBinaryResponse(Resource binaryData, RestResponse restResponse) {
		restResponse.setData(binaryData);
		return restResponse;
	}

	private RestResponse createErrorResponse(Throwable throwable) {
		RestResponse errorResponse = new RestResponse();
		errorResponse.setStatus(500);
		errorResponse.setData("Error occurred: " + throwable.getMessage());
		return errorResponse;
	}

}
