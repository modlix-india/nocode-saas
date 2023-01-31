package com.fincity.security.controller;

import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.saas.commons.jooq.controller.AbstractJOOQUpdatableDataController;
import com.fincity.security.dao.ClientDAO;
import com.fincity.security.dto.Client;
import com.fincity.security.jooq.tables.records.SecurityClientRecord;
import com.fincity.security.service.ClientService;

import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

@RestController
@RequestMapping("api/security/clients")
public class ClientController
        extends AbstractJOOQUpdatableDataController<SecurityClientRecord, ULong, Client, ClientDAO, ClientService> {
	
	@Autowired
	private ClientService clientService;

	@GetMapping("/internal/isBeingManaged")
	public Mono<ResponseEntity<Boolean>> isBeingManaged(@RequestParam String managingClientCode,
	        @RequestParam String clientCode) {

		return this.service.isBeingManagedBy(managingClientCode, clientCode)
		        .map(ResponseEntity::ok);
	}

	@GetMapping("/internal/isUserBeingManaged")
	public Mono<ResponseEntity<Boolean>> isUserBeingManaged(@RequestParam ULong userId,
	        @RequestParam String clientCode) {

		return this.service.isUserBeingManaged(userId, clientCode)
		        .map(ResponseEntity::ok);
	}

	@GetMapping("/internal/getClientNAppCode")
	public Mono<ResponseEntity<Tuple2<String, String>>> getClientNAppCode(@RequestParam String scheme,
	        @RequestParam String host, @RequestParam String port) {
		return this.service.getClientPattern(scheme, host, port)
		        .map(e -> Tuples.of(e.getClientCode(), e.getAppCode()))
		        .defaultIfEmpty(Tuples.of("SYSTEM", "nothing"))
		        .map(ResponseEntity::ok);
	}

	@GetMapping("/{clientId}/assignPackage/{packageId}")
	public Mono<ResponseEntity<Boolean>> assignPackage(@PathVariable ULong clientId, @PathVariable ULong packageId) {
		return clientService.assignPackageToClient(clientId, packageId)
		        .map(ResponseEntity::ok);
	}

	@GetMapping("/{clientId}/removePackage/{packageId}")
	public Mono<ResponseEntity<Boolean>> removePackage(@PathVariable ULong clientId, @PathVariable ULong packageId) {
		return clientService.removePackageFromClient(clientId, packageId)
		        .map(ResponseEntity::ok);
	}

}
