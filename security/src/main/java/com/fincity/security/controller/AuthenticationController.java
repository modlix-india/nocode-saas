package com.fincity.security.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.security.model.AuthenticationResponse;
import com.fincity.security.model.AuthenticationRequest;
import com.fincity.security.service.AuthenticationService;

import reactor.core.publisher.Mono;

@RestController
@RequestMapping("api/security/")
public class AuthenticationController {
	
	@Autowired
	private AuthenticationService service;

	@PostMapping("authenticate")
	public Mono<ResponseEntity<AuthenticationResponse>> authenticate(@RequestBody AuthenticationRequest authRequest,
	        ServerHttpRequest request, ServerHttpResponse response) {

		return this.service.authenticate(authRequest, request, response).map(ResponseEntity::ok);
	}
}
