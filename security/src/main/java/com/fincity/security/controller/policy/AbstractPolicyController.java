package com.fincity.security.controller.policy;

import java.util.Map;

import org.jooq.UpdatableRecord;
import org.jooq.types.ULong;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.fincity.saas.commons.jooq.controller.AbstractJOOQUpdatableDataController;
import com.fincity.security.dao.policy.AbstractPolicyDao;
import com.fincity.security.dto.policy.AbstractPolicy;
import com.fincity.security.service.policy.AbstractPolicyService;

import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

public class AbstractPolicyController<R extends UpdatableRecord<R>, D extends AbstractPolicy, O extends AbstractPolicyDao<R, D>, S extends AbstractPolicyService<R, D, O>>
		extends AbstractJOOQUpdatableDataController<R, ULong, D, O, S> {

	@GetMapping("/codes/policy")
	public Mono<ResponseEntity<D>> getClientAppPolicy(ServerHttpRequest request) {
		return this.getClientCodeAppCode(request)
				.flatMap(code -> this.service.getClientAppPolicy(code.getT1(), code.getT2())
						.map(ResponseEntity::ok));
	}

	@PostMapping("/codes")
	public Mono<ResponseEntity<D>> create(ServerHttpRequest request,
			@RequestBody D entity) {
		return this.getClientCodeAppCode(request)
				.flatMap(code -> this.service.create(code.getT1(), code.getT2(), entity)
						.map(ResponseEntity::ok));
	}

	@PatchMapping("/codes")
	public Mono<ResponseEntity<D>> patch(ServerHttpRequest request,
			@RequestBody Map<String, Object> entityMap) {
		return this.getClientCodeAppCode(request)
				.flatMap(code -> this.service.update(code.getT1(), code.getT2(), entityMap)
						.map(ResponseEntity::ok));
	}

	@DeleteMapping("/codes")
	public Mono<ResponseEntity<Integer>> delete(ServerHttpRequest request) {
		return this.getClientCodeAppCode(request)
				.flatMap(code -> this.service.delete(code.getT1(), code.getT2())
						.map(ResponseEntity::ok));
	}

	private Mono<Tuple2<String, String>> getClientCodeAppCode(ServerHttpRequest request) {

		String appCode = request.getHeaders().getFirst("appCode");
		String clientCode = request.getHeaders().getFirst("clientCode");

		return Mono.just(Tuples.of(clientCode, appCode));
	}

}
