package com.fincity.security.controller;

import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.saas.commons.jooq.controller.AbstractJOOQDataController;
import com.fincity.security.dao.ClientDAO;
import com.fincity.security.dto.Client;
import com.fincity.security.jooq.tables.records.SecurityClientRecord;
import com.fincity.security.service.ClientService;

import reactor.core.publisher.Mono;

@RestController
@RequestMapping("api/security/clients")
public class ClientController
        extends AbstractJOOQDataController<SecurityClientRecord, ULong, Client, ClientDAO, ClientService> {

	@Autowired
	private ClientService clientService;

	@GetMapping("/{clientId}/assignPackage/{packageId}")
	public Mono<ResponseEntity<Boolean>> assignPackage(@PathVariable ULong clientId, @PathVariable ULong packageId) {
		return clientService.assignPackageToClient(clientId, packageId)
		        .map(ResponseEntity::ok);
	}
}
