package com.fincity.saas.core.service.connection.rest;

import java.util.Map;

import org.springframework.util.MultiValueMap;

import com.fincity.saas.core.document.Connection;
import com.fincity.saas.core.dto.RestResponse;
import com.google.gson.JsonElement;

import reactor.core.publisher.Mono;

public interface IRestService {
	public Mono<RestResponse> call(String url, MultiValueMap<String, String> headers, String[] pathParameters,
			Map<String, String> queryParameters, int timeout, Connection connection, String method, JsonElement payload);
}
