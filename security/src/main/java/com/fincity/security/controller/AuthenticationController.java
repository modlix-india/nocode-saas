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
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.security.util.ServerHttpRequestUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.security.model.AuthenticationRequest;
import com.fincity.security.model.AuthenticationResponse;
import com.fincity.security.service.AuthenticationService;
import com.fincity.security.service.ClientService;

import reactor.core.publisher.Mono;
import reactor.util.context.Context;
import reactor.util.function.Tuple2;

@RestController
@RequestMapping("api/security/")
public class AuthenticationController {

	@Autowired
	private AuthenticationService service;

	@Autowired
	private ClientService clientService;

	@PostMapping("authenticate")
	public Mono<ResponseEntity<AuthenticationResponse>> authenticate(@RequestBody AuthenticationRequest authRequest,
			ServerHttpRequest request, ServerHttpResponse response) {

		return this.service.authenticate(authRequest, request, response).map(ResponseEntity::ok);
	}

	@PostMapping("authenticate/social")
	public Mono<ResponseEntity<AuthenticationResponse>> authenticateWSocial(
			@RequestBody AuthenticationRequest authRequest,
			ServerHttpRequest request, ServerHttpResponse response) {
		return this.service.authenticateWSocial(authRequest, request, response).map(ResponseEntity::ok);
	}

	@GetMapping(value = "revoke")
	public Mono<ResponseEntity<Void>> revoke(ServerHttpRequest request) {
		return this.service.revoke(request).map(e -> ResponseEntity.ok().build());
	}

	@GetMapping(value = "refreshToken")
	public Mono<ResponseEntity<AuthenticationResponse>> refreshToken(ServerHttpRequest request) {

		return this.service.refreshToken(request).map(ResponseEntity::ok);
	}

	@GetMapping(value = "verifyToken")
	public Mono<ResponseEntity<AuthenticationResponse>> verifyToken(ServerHttpRequest request) {

		return FlatMapUtil.flatMapMono(

				SecurityContextUtil::getUsersContextAuthentication,

				ca -> {

					if (ca.isAuthenticated())
						return Mono.just(ca);

					Tuple2<Boolean, String> tuple = ServerHttpRequestUtil.extractBasicNBearerToken(request);

					Mono<ContextAuthentication> errorMono;
					if (tuple.getT2().isBlank())
						errorMono = Mono.error(new GenericException(HttpStatus.FORBIDDEN, "Forbidden"));
					else
						errorMono = Mono.error(new GenericException(HttpStatus.UNAUTHORIZED, "Unauthorized"));

					return this.service.revoke(request).flatMap(e -> Mono.defer(() -> errorMono));

				},

				(ca, ca2) -> this.clientService.getClientInfoById(ca.getUser().getClientId()),

				(ca, ca2,
						client) -> Mono.just(new AuthenticationResponse().setUser(ca.getUser()).setClient(client)
								.setLoggedInClientCode(ca.getLoggedInFromClientCode())
								.setLoggedInClientId(ca.getLoggedInFromClientId())
								.setAccessToken(ca.getAccessToken())
								.setAccessTokenExpiryAt(ca.getAccessTokenExpiryAt())),

				(ca, ca2, client, vr) -> Mono.just(ResponseEntity.<AuthenticationResponse>ok(vr)))
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "AuthenticationController.verifyToken"));

	}

	@GetMapping(value = "internal/securityContextAuthentication", produces = { "application/json" })
	public Mono<ResponseEntity<ContextAuthentication>> contextAuthentication() {

		return flatMapMono(

				SecurityContextUtil::getUsersContextAuthentication,

				contextAuthentication -> Mono.just(ResponseEntity.<ContextAuthentication>ok(contextAuthentication)))
				.contextWrite(Context.of(LogUtil.METHOD_NAME, "AuthenticationController.contextAuthentication"));
	}

}
