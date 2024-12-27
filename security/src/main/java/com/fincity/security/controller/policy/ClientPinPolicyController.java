package com.fincity.security.controller.policy;

import org.jooq.types.ULong;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.saas.commons.jooq.controller.AbstractJOOQUpdatableDataController;
import com.fincity.security.dao.policy.ClientPinPolicyDAO;
import com.fincity.security.dto.policy.ClientPinPolicy;
import com.fincity.security.jooq.tables.records.SecurityClientPinPolicyRecord;
import com.fincity.security.service.policy.ClientPinPolicyService;

import reactor.core.publisher.Mono;

@RestController
@RequestMapping("api/security/clientPinPolicy")
public class ClientPinPolicyController extends
		AbstractJOOQUpdatableDataController<SecurityClientPinPolicyRecord, ULong, ClientPinPolicy, ClientPinPolicyDAO, ClientPinPolicyService> {

	@GetMapping("/client")
	public Mono<ResponseEntity<ClientPinPolicy>> getClientAppPolicy(ServerHttpRequest request) {

		String appCode = request.getHeaders().getFirst("appCode");
		String clientCode = request.getHeaders().getFirst("clientCode");

		return this.service.getClientAppPolicy(clientCode, appCode).map(ResponseEntity::ok);
	}

}
