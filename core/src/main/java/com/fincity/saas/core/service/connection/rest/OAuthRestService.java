package com.fincity.saas.core.service.connection.rest;

import org.springframework.stereotype.Service;

import com.fincity.saas.core.document.Connection;
import com.fincity.saas.core.dto.RestRequest;
import com.fincity.saas.core.dto.RestResponse;

import reactor.core.publisher.Mono;

@Service
public class OAuthRestService extends AbstractRestService implements IRestService {

	@Override
	public Mono<RestResponse> call(Connection connection, RestRequest request) {
		// TODO Auto-generated method stub
		return null;
	}

}
