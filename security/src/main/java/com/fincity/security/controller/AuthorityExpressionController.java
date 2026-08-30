package com.fincity.security.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.security.model.authority.AuthorityExpression;
import com.fincity.security.model.authority.AuthorityExpressionRequest;
import com.fincity.security.service.AuthorityExpressionService;

import reactor.core.publisher.Mono;

@RestController
@RequestMapping("api/security/authorities")
public class AuthorityExpressionController {

    private final AuthorityExpressionService authorityExpressionService;

    public AuthorityExpressionController(AuthorityExpressionService authorityExpressionService) {
        this.authorityExpressionService = authorityExpressionService;
    }

    @PostMapping("/parse")
    public Mono<ResponseEntity<AuthorityExpression>> parse(@RequestBody AuthorityExpressionRequest request) {
        return this.authorityExpressionService.parse(request.getExpression()).map(ResponseEntity::ok);
    }
}
