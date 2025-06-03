package com.fincity.security.controller;

import org.jooq.types.ULong;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.saas.commons.jooq.controller.AbstractJOOQUpdatableDataController;
import com.fincity.security.dao.ClientDAO;
import com.fincity.security.dto.Client;
import com.fincity.security.jooq.enums.SecurityAppRegIntegrationPlatform;
import com.fincity.security.jooq.tables.records.SecurityClientRecord;
import com.fincity.security.model.ClientRegistrationRequest;
import com.fincity.security.model.RegistrationResponse;
import com.fincity.security.model.otp.OtpGenerationRequest;
import com.fincity.security.service.ClientService;
import com.fincity.security.service.appregistration.ClientRegistrationService;

import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

@RestController
@RequestMapping("api/security/clients")
public class ClientController
        extends AbstractJOOQUpdatableDataController<SecurityClientRecord, ULong, Client, ClientDAO, ClientService> {

    private final ClientRegistrationService clientRegistrationService;

    public ClientController(ClientRegistrationService clientRegistrationService) {
        this.clientRegistrationService = clientRegistrationService;
    }

    @GetMapping("/internal/isBeingManaged")
    public Mono<ResponseEntity<Boolean>> isBeingManaged(@RequestParam String managingClientCode,
                                                        @RequestParam String clientCode) {

        return this.service.isBeingManagedBy(managingClientCode, clientCode).map(ResponseEntity::ok);
    }

    @GetMapping("/internal/isBeingManagedById")
    public Mono<ResponseEntity<Boolean>> isBeingManagedById(@RequestParam ULong managingClientId,
                                                            @RequestParam ULong clientId) {

        return this.service.isBeingManagedBy(managingClientId, clientId).map(ResponseEntity::ok);
    }

    @GetMapping("/internal/getClientById")
    public Mono<ResponseEntity<Client>> getClientById(@RequestParam ULong clientId) {

        return this.service.getClientInfoById(clientId).map(ResponseEntity::ok);
    }

    @GetMapping("/internal/getClientByCode")
    public Mono<ResponseEntity<Client>> getClientByCode(@RequestParam String clientCode) {

        return this.service.getClientBy(clientCode).map(ResponseEntity::ok);
    }

    @GetMapping("/internal/isUserBeingManaged")
    public Mono<ResponseEntity<Boolean>> isUserBeingManaged(@RequestParam ULong userId,
                                                            @RequestParam String clientCode) {

        return this.service.isUserBeingManaged(clientCode, userId).map(ResponseEntity::ok);
    }

    @GetMapping("/internal/validateClientCode")
    public Mono<ResponseEntity<Boolean>> validateClientCode(@RequestParam String clientCode) {

        return this.service.getClientBy(clientCode).flatMap(e -> Mono.just(e != null)).map(ResponseEntity::ok);
    }

    @GetMapping("/internal/getClientNAppCode")
    public Mono<ResponseEntity<Tuple2<String, String>>> getClientNAppCode(@RequestParam String scheme,
                                                                          @RequestParam String host, @RequestParam String port) {
        return this.service.getClientPattern(scheme, host, port).map(e -> Tuples.of(e.getClientCode(), e.getAppCode()))
                .defaultIfEmpty(Tuples.of("SYSTEM", "nothing")).map(ResponseEntity::ok);
    }

    @GetMapping("/makeClientActive")
    public Mono<ResponseEntity<Boolean>> makeClientActive(@RequestParam(required = false) ULong clientId) {

        return this.service.makeClientActiveIfInActive(clientId).map(ResponseEntity::ok);
    }

    @GetMapping("/makeClientInActive")
    public Mono<ResponseEntity<Boolean>> makeClientInActive(@RequestParam(required = false) ULong clientId) {

        return this.service.makeClientInActive(clientId).map(ResponseEntity::ok);
    }

    @PostMapping("/register/otp/generate")
    public Mono<ResponseEntity<Boolean>> generateCode(
            @RequestBody OtpGenerationRequest otpGenerationRequest,
            ServerHttpRequest request) {
        return this.clientRegistrationService.generateOtp(otpGenerationRequest, request)
                .map(ResponseEntity::ok);
    }

    @PostMapping("/register/otp/verify")
    public Mono<ResponseEntity<Boolean>> preRegisterCheckOne(
            @RequestBody ClientRegistrationRequest registrationRequest) {
        return this.clientRegistrationService.preRegisterCheckOne(registrationRequest).map(ResponseEntity::ok);
    }

    @PostMapping("/register")
    public Mono<ResponseEntity<RegistrationResponse>> register(ServerHttpRequest request,
                                                               ServerHttpResponse response, @RequestBody ClientRegistrationRequest registrationRequest) {
        return this.clientRegistrationService.register(registrationRequest, request, response).map(ResponseEntity::ok);
    }

    @PostMapping("/socialRegister")
    public Mono<ResponseEntity<RegistrationResponse>> socialRegister(ServerHttpRequest request,
                                                                     ServerHttpResponse response, @RequestBody ClientRegistrationRequest registrationRequest) {
        return this.clientRegistrationService.registerWSocial(request, response, registrationRequest)
                .map(ResponseEntity::ok);
    }

    @PostMapping("/socialRegister/evoke")
    public Mono<ResponseEntity<String>> evokeSocialRegister(ServerHttpRequest request,
                                                            @RequestParam SecurityAppRegIntegrationPlatform platform) {
        return this.clientRegistrationService.evokeRegisterWSocial(platform, request).map(ResponseEntity::ok);
    }

    @GetMapping("/socialRegister/callback")
    public Mono<ResponseEntity<Void>> socialRegisterCallback(ServerHttpRequest request, ServerHttpResponse response) {
        return this.clientRegistrationService.registerWSocialCallback(request, response)
                .then(Mono.just(ResponseEntity.ok().build()));
    }

    @GetMapping("/register/events")
    public Mono<ResponseEntity<Boolean>> evokeRegistrationEvents(ServerHttpRequest request, ServerHttpResponse response,
                                                                 @RequestBody ClientRegistrationRequest registrationRequest) {
        return this.clientRegistrationService.evokeRegistrationEvents(registrationRequest, request, response)
                .map(ResponseEntity::ok);
    }

}
