package com.fincity.saas.commons.mongo.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.mongo.document.Transport;
import com.fincity.saas.commons.mongo.model.TransportPOJO;
import com.fincity.saas.commons.mongo.model.TransportRequest;
import com.fincity.saas.commons.mongo.repository.TransportRepository;
import com.fincity.saas.commons.mongo.service.AbstractTransportService;
import com.fincity.saas.commons.util.LogUtil;

import reactor.core.publisher.Mono;
import reactor.util.context.Context;

public class AbstractTransportController
		extends AbstractOverridableDataController<Transport, TransportRepository, AbstractTransportService> {

	@Autowired
	private ObjectMapper mapper;

	@PostMapping("/makeTransport")
	public Mono<ResponseEntity<Transport>> makeTransport(@RequestBody TransportRequest request) {

		return this.service.makeTransport(request)
				.map(ResponseEntity::ok);
	}

	@GetMapping("/applyTransport/{id}")
	public Mono<ResponseEntity<Boolean>> applyTransport(@PathVariable("id") String transportId) {

		return this.service.applyTransport(transportId)
				.map(ResponseEntity::ok);
	}

	@GetMapping("/applyTransportCode/{code}")
	public Mono<ResponseEntity<Boolean>> applyTransportWithTransportCode(@PathVariable("code") String code) {

		return this.service.applyTransportWithTransportCode(code)
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
	public Mono<ResponseEntity<Transport>> createApply(@RequestBody TransportPOJO pojo) {

		Transport entity = this.mapper.convertValue(pojo, Transport.class);

		return FlatMapUtil.flatMapMono(

				() -> this.service.create(entity),

				c -> this.service.applyTransport(c.getId()),

				(c, applied) -> Mono.just(ResponseEntity.ok(c)))

				.contextWrite(Context.of(LogUtil.METHOD_NAME, "AbstractTransportController.createApply"));
	}
}
