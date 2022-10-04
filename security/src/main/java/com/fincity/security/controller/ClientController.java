package com.fincity.security.controller;

import org.jooq.types.ULong;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.saas.commons.jooq.controller.AbstractJOOQDataController;
import com.fincity.saas.commons.security.model.ClientUrlPattern;
import com.fincity.security.dao.ClientDAO;
import com.fincity.security.dto.Client;
import com.fincity.security.jooq.tables.records.SecurityClientRecord;
import com.fincity.security.service.ClientService;

import reactor.core.publisher.Mono;

@RestController
@RequestMapping("api/security/clients")
public class ClientController
        extends AbstractJOOQDataController<SecurityClientRecord, ULong, Client, ClientDAO, ClientService> {

	@GetMapping("/internal/isBeingManaged")
	public Mono<ResponseEntity<Boolean>> isBeingManaged(@RequestParam String managingClientCode,
	        @RequestParam String clientCode) {

		return this.service.isBeingManagedBy(managingClientCode, clientCode)
		        .map(ResponseEntity::ok);
	}
	
	@GetMapping("/internal/getClientCode")
	public Mono<ResponseEntity<String>> getClientCode(@RequestParam String scheme, @RequestParam String host, @RequestParam String port) {
		return this.service.getClientPattern(
		        scheme, host, port)
		        .map(ClientUrlPattern::getClientCode)
		        .defaultIfEmpty("SYSTEM")
		        .map(ResponseEntity::ok);
	}
}
