package com.fincity.security.controller;

import org.jooq.types.ULong;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.saas.commons.jooq.controller.AbstractJOOQUpdatableDataController;
import com.fincity.security.dao.AppDAO;
import com.fincity.security.dto.App;
import com.fincity.security.jooq.tables.records.SecurityAppRecord;
import com.fincity.security.model.ApplicationAccessRequest;
import com.fincity.security.service.AppService;

import reactor.core.publisher.Mono;

@RestController
@RequestMapping("api/security/apps")
public class AppController
        extends AbstractJOOQUpdatableDataController<SecurityAppRecord, ULong, App, AppDAO, AppService> {

	@GetMapping("/internal/hasReadAccess")
	public Mono<ResponseEntity<Boolean>> hasReadAccess(@RequestParam String appCode,
	        @RequestParam String clientCode) {

		return this.service.hasReadAccess(appCode, clientCode)
		        .map(ResponseEntity::ok);
	} 
	
	@GetMapping("/internal/hasWriteAccess")
	public Mono<ResponseEntity<Boolean>> hasWriteAccess(@RequestParam String appCode,
	        @RequestParam String clientCode) {

		return this.service.hasWriteAccess(appCode, clientCode)
		        .map(ResponseEntity::ok);
	}
	
	@PostMapping("/{id}/access")
	public Mono<ResponseEntity<Boolean>> addClientAccess(@PathVariable(PATH_VARIABLE_ID) final ULong appId, @RequestBody final ApplicationAccessRequest request) {
		return this.service.addClientAccess(appId, request.getClientId(), request.isWriteAccess()).map(ResponseEntity::ok);
	}
	
	@PatchMapping("/{id}/access")
	public Mono<ResponseEntity<Boolean>> updateClientAccess(@PathVariable(PATH_VARIABLE_ID) final ULong appId, @RequestBody final ApplicationAccessRequest request) {
		return this.service.updateClientAccess(request.getId(), request.isWriteAccess()).map(ResponseEntity::ok);
	}
	
	@DeleteMapping("/{id}/access")
	public Mono<ResponseEntity<Boolean>> removeClientAccess(@PathVariable(PATH_VARIABLE_ID) final ULong appId, @RequestParam final ULong accessId) {
		return this.service.removeClient(appId, accessId).map(ResponseEntity::ok);
	}
}
