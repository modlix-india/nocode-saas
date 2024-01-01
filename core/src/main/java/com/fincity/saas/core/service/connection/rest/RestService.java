package com.fincity.saas.core.service.connection.rest;

import java.net.URL;
import java.util.EnumMap;
import java.util.Map;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;

import com.fincity.nocode.kirun.engine.util.string.StringUtil;
import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.core.dto.RestResponse;
import com.fincity.saas.core.enums.ConnectionSubType;
import com.fincity.saas.core.enums.ConnectionType;
import com.fincity.saas.core.service.ConnectionService;
import com.fincity.saas.core.service.CoreMessageResourceService;
import com.google.gson.JsonElement;

import reactor.core.publisher.Mono;
import reactor.util.context.Context;
import reactor.util.function.Tuples;

@Service
public class RestService {
	@Autowired
	private ConnectionService connectionService;

	@Autowired
	private CoreMessageResourceService msgService;

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

	public Mono<RestResponse> doCall(String appCode, String clientCode, String connectionName, String url,
			MultiValueMap<String, String> headers, String[] pathParameters, Map<String, String> queryParameters,
			int timeout, String method, JsonElement payload) {

		return FlatMapUtil.flatMapMonoWithNull(() -> {
			if (!StringUtil.isNullOrBlank(appCode) && !StringUtil.isNullOrBlank(clientCode))
				return Mono.just(Tuples.of(appCode, clientCode));
			return SecurityContextUtil.getUsersContextAuthentication()
					.map(e -> Tuples.of(e.getUrlAppCode(), e.getUrlClientCode()));
		}, codeTuple -> {
			if (!connectionName.isBlank()) {
				return connectionService
						.find(connectionName, codeTuple.getT1(), codeTuple.getT2(), ConnectionType.REST_API)
						.switchIfEmpty(msgService.throwMessage(msg -> new GenericException(HttpStatus.NOT_FOUND, msg),
								CoreMessageResourceService.CONNECTION_DETAILS_MISSING, connectionName));
			}
			try {
				new URL(url);
			} catch (Exception e) {
				msgService.throwMessage(msg -> new GenericException(HttpStatus.BAD_REQUEST, msg),
						CoreMessageResourceService.CONNECTION_DETAILS_MISSING, url);
			}
			return Mono.empty();
		}, (codeTuple, connection) -> {
			return Mono.just(this.services
					.get(connection != null ? connection.getConnectionSubType() : ConnectionSubType.REST_API_BASIC));
		}, (codeTuple, connection, service) -> service.call(url, headers, pathParameters, queryParameters, timeout,
				connection, method, payload)

		).contextWrite(Context.of(LogUtil.METHOD_NAME, "RestService.doCall"));

	}

}
