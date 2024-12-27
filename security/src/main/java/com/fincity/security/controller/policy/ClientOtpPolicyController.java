package com.fincity.security.controller.policy;

import org.jooq.types.ULong;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.saas.commons.jooq.controller.AbstractJOOQUpdatableDataController;
import com.fincity.security.dao.policy.ClientOtpPolicyDAO;
import com.fincity.security.dto.policy.ClientOtpPolicy;
import com.fincity.security.jooq.tables.records.SecurityClientOtpPolicyRecord;
import com.fincity.security.service.policy.ClientOtpPolicyService;

import reactor.core.publisher.Mono;

@RestController
@RequestMapping("api/security/clientOtpPolicy")
public class ClientOtpPolicyController extends
		AbstractJOOQUpdatableDataController<SecurityClientOtpPolicyRecord, ULong, ClientOtpPolicy, ClientOtpPolicyDAO, ClientOtpPolicyService> {

	@GetMapping("/client")
	public Mono<ResponseEntity<ClientOtpPolicy>> getClientAppPolicy(ServerHttpRequest request) {

		String appCode = request.getHeaders().getFirst("appCode");
		String clientCode = request.getHeaders().getFirst("clientCode");

		return this.service.getClientAppPolicy(clientCode, appCode).map(ResponseEntity::ok);
	}

}
