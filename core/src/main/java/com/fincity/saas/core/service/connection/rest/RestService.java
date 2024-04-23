package com.fincity.saas.core.service.connection.rest;

import java.util.EnumMap;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.fincity.nocode.kirun.engine.util.string.StringUtil;
import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.core.dto.RestRequest;
import com.fincity.saas.core.dto.RestResponse;
import com.fincity.saas.core.enums.ConnectionSubType;
import com.fincity.saas.core.enums.ConnectionType;
import com.fincity.saas.core.service.ConnectionService;

import reactor.core.publisher.Mono;
import reactor.util.context.Context;
import reactor.util.function.Tuples;

@Service
public class RestService {
	@Autowired
	private ConnectionService connectionService;

	@Autowired
	private BasicRestService basicRestService;

	@Autowired
	private OAuthRestService oAuthRestService;

	private EnumMap<ConnectionSubType, IRestService> services = new EnumMap<>(ConnectionSubType.class);

	@PostConstruct
	public void init() {
		this.services.put(ConnectionSubType.REST_API_BASIC, basicRestService);
		this.services.put(ConnectionSubType.REST_API_OAUTH2, oAuthRestService);
	}

	public Mono<RestResponse> doCall(String appCode, String clientCode, String connectionName, RestRequest request) {

		return FlatMapUtil.flatMapMono(

				() -> {
					if (!StringUtil.isNullOrBlank(appCode) && !StringUtil.isNullOrBlank(clientCode))
						return Mono.just(Tuples.of(appCode, clientCode));
					return SecurityContextUtil.getUsersContextAuthentication()
							.map(e -> Tuples.of(e.getUrlAppCode(), e.getUrlClientCode()));
				},

				codeTuple -> connectionService
						.read(connectionName, codeTuple.getT1(), codeTuple.getT2(), ConnectionType.REST_API),
				(codeTuple, connection) -> {
					return Mono.just(this.services.get(
							connection != null ? connection.getConnectionSubType() : ConnectionSubType.REST_API_BASIC));
				}, (codeTuple, connection, service) -> service.call(connection, request)

		).contextWrite(Context.of(LogUtil.METHOD_NAME, "RestService.doCall"))
				.switchIfEmpty(Mono.defer(() -> Mono.just(new RestResponse().setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value()).setData("Connection Not found"))));

	}

}
