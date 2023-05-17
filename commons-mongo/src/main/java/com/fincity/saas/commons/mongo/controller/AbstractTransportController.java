package com.fincity.saas.commons.mongo.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.fincity.saas.commons.mongo.document.Transport;
import com.fincity.saas.commons.mongo.model.TransportRequest;
import com.fincity.saas.commons.mongo.repository.TransportRepository;
import com.fincity.saas.commons.mongo.service.AbstractTransportService;

import reactor.core.publisher.Mono;

public class AbstractTransportController
        extends AbstractOverridableDataController<Transport, TransportRepository, AbstractTransportService> {

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

	@GetMapping("/transportTypes")
	public Mono<ResponseEntity<List<String>>> transportTypes() {
		return Mono.just(ResponseEntity.ok(this.service.getServieMap()
		        .stream()
		        .map(e -> e.getObjectName())
		        .toList()));
	}
}
