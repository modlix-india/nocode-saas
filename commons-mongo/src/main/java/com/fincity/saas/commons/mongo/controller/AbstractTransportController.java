package com.fincity.saas.commons.mongo.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.mongo.document.Transport;
import com.fincity.saas.commons.mongo.model.TransportRequest;
import com.fincity.saas.commons.mongo.repository.TransportRepository;
import com.fincity.saas.commons.mongo.service.AbstractTransportService;
import com.fincity.saas.commons.util.LogUtil;

import reactor.core.publisher.Mono;
import reactor.util.context.Context;

public abstract class AbstractTransportController
		extends AbstractOverridableDataController<Transport, TransportRepository, AbstractTransportService> {

	private final ObjectMapper mapper;

	protected AbstractTransportController(ObjectMapper objectMapper) {
		this.mapper = objectMapper;
	}

	@PostMapping("/makeTransport")
	public Mono<ResponseEntity<Transport>> makeTransport(@RequestBody TransportRequest request) {

		return this.service.makeTransport(request)
				.map(ResponseEntity::ok);
	}

	@GetMapping("/applyTransport/{id}")
	public Mono<ResponseEntity<Boolean>> applyTransport(@RequestHeader("X-Forwarded-Host") String forwardedHost,
			@RequestHeader("X-Forwarded-Port") String forwardedPort, @PathVariable("id") String transportId) {

		return this.service.applyTransport(forwardedHost, forwardedPort, transportId, null, false)
				.map(ResponseEntity::ok);
	}

	@GetMapping("/applyTransportCode/{code}")
	public Mono<ResponseEntity<Boolean>> applyTransportWithTransportCode(
			@RequestHeader("X-Forwarded-Host") String forwardedHost,
			@RequestHeader("X-Forwarded-Port") String forwardedPort, @PathVariable("code") String code) {

		return this.service.applyTransportWithTransportCode(forwardedHost, forwardedPort, code)
				.map(ResponseEntity::ok);
	}

	@GetMapping("/transportTypes")
	public Mono<ResponseEntity<List<String>>> transportTypes() {
		return Mono.just(ResponseEntity.ok(this.service.getServieMap()
				.stream()
				.map(e -> e.getObjectName())
				.toList()));
	}

	@PostMapping("/createAndApply")
	public Mono<ResponseEntity<Transport>> createApply(
			@RequestHeader("X-Forwarded-Host") String forwardedHost,
			@RequestHeader("X-Forwarded-Port") String forwardedPort,
			@RequestParam(defaultValue = "false") Boolean isForBaseApp,
			@RequestParam(required = false) String applicationCode,
			@RequestBody Transport pojo) {

		Transport entity = this.mapper.convertValue(pojo, Transport.class);

		return FlatMapUtil.flatMapMono(

				() -> this.service.create(entity),

				c -> this.service.applyTransport(forwardedHost, forwardedPort, c.getId(), applicationCode,
						isForBaseApp),

				(c, applied) -> Mono.just(ResponseEntity.ok(c)))

				.contextWrite(Context.of(LogUtil.METHOD_NAME, "AbstractTransportController.createApply"));
	}
}
