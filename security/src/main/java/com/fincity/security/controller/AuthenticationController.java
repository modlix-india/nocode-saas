package com.fincity.security.controller;

import com.fincity.security.dto.UserAccess;
import com.fincity.security.model.MakeOneTimeTimeTokenRequest;
import com.fincity.security.model.UserAppAccessRequest;
import jakarta.ws.rs.PathParam;
import org.jooq.types.ULong;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.*;

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

import java.util.Map;

@RestController
@RequestMapping("api/security/")
public class AuthenticationController {

    private final AuthenticationService service;
    private final ClientService clientService;

    public AuthenticationController(AuthenticationService service, ClientService clientService) {
        this.service = service;
        this.clientService = clientService;
    }

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

    @PostMapping("authenticate/otp/generate")
    public Mono<ResponseEntity<Boolean>> generateOtp(@RequestBody AuthenticationRequest authRequest,
            ServerHttpRequest request) {
        return this.service.generateOtp(authRequest, request).map(ResponseEntity::ok);
    }

    @GetMapping(value = "revoke")
    public Mono<ResponseEntity<Void>> revoke(
            @RequestParam(name = "ssoLogout", defaultValue = "false", required = false) boolean ssoLogout,
            ServerHttpRequest request) {
        return this.service.revoke(ssoLogout, request).map(e -> ResponseEntity.ok().build());
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

                    return this.service.revoke(false, request).flatMap(e -> Mono.defer(() -> errorMono));

                },

                (ca, ca2) -> this.clientService.getClientInfoById(ca.getUser().getClientId()),

                (ca, ca2, client) -> {

                    return this.clientService.getManagedClientOfClientById(client.getId())
                            .map(mc -> new AuthenticationResponse().setUser(ca.getUser()).setClient(client)
                                    .setVerifiedAppCode(ca.getVerifiedAppCode())
                                    .setLoggedInClientCode(ca.getLoggedInFromClientCode())
                                    .setLoggedInClientId(ca.getLoggedInFromClientId())
                                    .setAccessToken(ca.getAccessToken())
                                    .setAccessTokenExpiryAt(ca.getAccessTokenExpiryAt())
                                    .setManagedClientCode(mc.getCode())
                                    .setManagedClientId(mc.getId() != null ? mc.getId().toBigInteger() : null));
                },

                (ca, ca2, client, vr) -> Mono.<ResponseEntity<AuthenticationResponse>>just(ResponseEntity.ok(vr)))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "AuthenticationController.verifyToken"));

    }

    @PostMapping(value = "/makeOneTimeToken")
    public Mono<ResponseEntity<Map<String, String>>> makeOneTimeToken(@RequestBody MakeOneTimeTimeTokenRequest request,
            ServerHttpRequest httpRequest) {
        return this.service.makeOneTimeToken(request, httpRequest).map(ResponseEntity::ok);
    }

    @GetMapping(value = "/authenticateWithOneTimeToken/{pathToken}")
    public Mono<ResponseEntity<ContextAuthentication>> authenticateWithOneTimeToken(
            @PathVariable(required = false) String pathToken, @RequestParam(required = false) String token,
            ServerHttpRequest request, ServerHttpResponse response) {
        return this.service.authenticateWithOneTimeToken(pathToken == null ? token : pathToken, request, response)
                .map(AuthenticationResponse::makeContextAuthentication)
                .map(ResponseEntity::ok);
    }

    @GetMapping(value = "internal/securityContextAuthentication", produces = { "application/json" })
    public Mono<ResponseEntity<ContextAuthentication>> contextAuthentication() {

        return FlatMapUtil.flatMapMono(

                SecurityContextUtil::getUsersContextAuthentication,

                contextAuthentication -> Mono.just(ResponseEntity.ok(contextAuthentication)))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "AuthenticationController.contextAuthentication"));
    }

    @PostMapping("user/access")
    public Mono<ResponseEntity<UserAccess>> getUserAccess(@RequestBody UserAppAccessRequest request,
            ServerHttpRequest httpRequest) {
        return this.service.getUserAppAccess(request, httpRequest).map(ResponseEntity::ok);
    }

}
