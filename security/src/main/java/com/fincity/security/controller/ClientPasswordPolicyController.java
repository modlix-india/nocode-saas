package com.fincity.security.controller;

import org.jooq.types.ULong;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.saas.commons.jooq.controller.AbstractJOOQUpdatableDataController;
import com.fincity.security.dao.ClientPasswordPolicyDAO;
import com.fincity.security.dto.ClientPasswordPolicy;
import com.fincity.security.jooq.tables.records.SecurityClientPasswordPolicyRecord;
import com.fincity.security.service.ClientPasswordPolicyService;

import reactor.core.publisher.Mono;

@RestController
@RequestMapping("api/security/clientPasswordPolicy")
public class ClientPasswordPolicyController extends
        AbstractJOOQUpdatableDataController<SecurityClientPasswordPolicyRecord, ULong, ClientPasswordPolicy, ClientPasswordPolicyDAO, ClientPasswordPolicyService> {


	@GetMapping("/{clientId}/fetchPolicy/{appId}")
	public Mono<ResponseEntity<ClientPasswordPolicy>> getByClientAndAppIds(@PathVariable ULong clientId,
	        @PathVariable ULong appId) {
		return this.service.fetchPolicyByAppandClientIds(appId, clientId)
		        .map(ResponseEntity::ok);
	}
}
