package com.fincity.security.controller;

import static com.fincity.nocode.reactor.util.FlatMapUtil.flatMapMono;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.common.security.jwt.ContextAuthentication;
import com.fincity.saas.common.security.jwt.VerificationResponse;
import com.fincity.saas.common.security.util.SecurityContextUtil;
import com.fincity.saas.common.security.util.ServerHttpRequestUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.security.model.AuthenticationRequest;
import com.fincity.security.model.AuthenticationResponse;
import com.fincity.security.service.AuthenticationService;

import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;

@RestController
@RequestMapping("api/security/")
public class AuthenticationController {

	@Autowired
	private AuthenticationService service;

	@PostMapping("authenticate")
	public Mono<ResponseEntity<AuthenticationResponse>> authenticate(@RequestBody AuthenticationRequest authRequest,
	        ServerHttpRequest request, ServerHttpResponse response) {

		return this.service.authenticate(authRequest, request, response)
		        .map(ResponseEntity::ok);
	}

	@GetMapping(value = "revoke")
	public Mono<ResponseEntity<Void>> revoke(ServerHttpRequest request) {
		return this.service.revoke(request)
		        .map(e -> ResponseEntity.ok()
		                .build());
	}

	@GetMapping(value = "verifyToken")
	public Mono<ResponseEntity<VerificationResponse>> verifyToken(ServerHttpRequest request) {

		return FlatMapUtil.flatMapMonoLog(

		        SecurityContextUtil::getUsersContextAuthentication,

		        ca -> {
		        	
		        	if (!ca.isAuthenticated()) {
		        		
		        		Tuple2<Boolean, String> tuple = ServerHttpRequestUtil.extractBasicNBearerToken(request);
		        		
		        		if (tuple.getT2().isBlank())
		        			return Mono.error(new GenericException(HttpStatus.FORBIDDEN, "Forbidden"));
		        		else
		        			return Mono.error(new GenericException(HttpStatus.UNAUTHORIZED, "Unauthorized"));
		        	}
		        	
		        	return Mono.just(ca);
		        },
		        
		        (ca, ca2) -> Mono.just(new VerificationResponse().setUser(ca.getUser())
		                .setAccessToken(ca.getAccessToken())
		                .setAccessTokenExpiryAt(ca.getAccessTokenExpiryAt())),

		        (ca, ca2, vr) -> Mono.just(ResponseEntity.<VerificationResponse>ok(vr)));

	}

	@GetMapping(value = "internal/securityContextAuthentication", produces = { "application/json" })
	public Mono<ResponseEntity<ContextAuthentication>> contextAuthentication() {

		return flatMapMono(

		        SecurityContextUtil::getUsersContextAuthentication,

		        contextAuthentication -> Mono.just(ResponseEntity.<ContextAuthentication>ok(contextAuthentication)));
	}

}
