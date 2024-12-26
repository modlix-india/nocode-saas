package com.fincity.security.controller.policy;

import org.jooq.types.ULong;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.saas.commons.jooq.controller.AbstractJOOQUpdatableDataController;
import com.fincity.security.dao.policy.ClientPasswordPolicyDAO;
import com.fincity.security.dto.policy.ClientPasswordPolicy;
import com.fincity.security.jooq.tables.records.SecurityClientPasswordPolicyRecord;
import com.fincity.security.service.policy.ClientPasswordPolicyService;

import reactor.core.publisher.Mono;

@RestController
@RequestMapping("api/security/clientPasswordPolicy")
public class ClientPasswordPolicyController extends
        AbstractJOOQUpdatableDataController<SecurityClientPasswordPolicyRecord, ULong, ClientPasswordPolicy, ClientPasswordPolicyDAO, ClientPasswordPolicyService> {

	@PostMapping("/client")
	public Mono<ResponseEntity<ClientPasswordPolicy>> getClientAppPolicy(ServerHttpRequest request) {

		String appCode = request.getHeaders().getFirst("appCode");
		String clientCode = request.getHeaders().getFirst("clientCode");

		return this.service.getClientAppPolicy(clientCode, appCode).map(ResponseEntity::ok);
	}

}
