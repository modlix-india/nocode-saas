package com.fincity.saas.commons.core.service.connection.rest;

import com.fincity.saas.commons.core.document.Connection;
import com.fincity.saas.commons.core.dto.RestRequest;
import com.fincity.saas.commons.core.dto.RestResponse;
import reactor.core.publisher.Mono;

public interface IRestService {

    Mono<RestResponse> call(Connection connection, RestRequest request);

    Mono<RestResponse> call(Connection connection, RestRequest request, boolean fileDownload);
}
