package com.fincity.saas.core.controller;

import com.fincity.saas.core.enums.ConnectionType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.saas.commons.mongo.controller.AbstractOverridableDataController;
import com.fincity.saas.core.document.Connection;
import com.fincity.saas.core.repository.ConnectionRepository;
import com.fincity.saas.core.service.ConnectionService;
import com.fincity.saas.core.service.connection.rest.OAuth2RestService;

import reactor.core.publisher.Mono;

@RestController
@RequestMapping("api/core/connections")
public class ConnectionController
        extends AbstractOverridableDataController<Connection, ConnectionRepository, ConnectionService> {

    private final OAuth2RestService oAuth2RestService;

    public ConnectionController(OAuth2RestService oAuth2RestService) {
        this.oAuth2RestService = oAuth2RestService;
    }

    @PostMapping("/oauth/evoke")
    public Mono<ResponseEntity<String>> evokeOAuth(@RequestParam(required = true) String connectionName,
            ServerHttpRequest request) {

        return this.oAuth2RestService.evokeConsentAuth(connectionName, request).map(ResponseEntity::ok);

    }

    @GetMapping("/oauth/callback")
    public Mono<ResponseEntity<Void>> oAuthCallback(ServerHttpRequest request, ServerHttpResponse response) {

        return this.oAuth2RestService.oAuth2Callback(request, response).map(ResponseEntity::ok);

    }

    @GetMapping("/oauth/revoke")
    public Mono<ResponseEntity<Boolean>> revokeToken(@RequestParam(required = true) String connectionName) {

        return this.oAuth2RestService.revokeConnectionToken(connectionName).map(ResponseEntity::ok);
    }

    @GetMapping("/oauth2/token/{connectionName}")
    public Mono<String> getOAuth2Token(@PathVariable("connectionName") String connectionName) {

        return this.oAuth2RestService.getAccessToken(connectionName);
    }

}
