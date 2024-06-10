package com.fincity.saas.core.service.connection.rest;

import com.fincity.saas.core.document.Connection;
import com.fincity.saas.core.dto.RestRequest;
import com.fincity.saas.core.dto.RestResponse;

import reactor.core.publisher.Mono;

public interface IRestService {

	Mono<RestResponse> call(Connection connection, RestRequest request);

	Mono<RestResponse> call(Connection connection, RestRequest request, boolean fileDownload);
}
