package com.fincity.security.controller;

import java.beans.PropertyEditorSupport;
import java.util.HashMap;
import java.util.List;

import org.jooq.types.UInteger;
import org.jooq.types.ULong;
import org.jooq.types.UShort;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.validation.DataBinder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.model.Query;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.util.ConditionUtil;
import com.fincity.security.dto.SSLCertificate;
import com.fincity.security.model.SSLCertificateConfiguration;
import com.fincity.security.model.SSLCertificateOrder;
import com.fincity.security.model.SSLCertificateOrderRequest;
import com.fincity.security.service.SSLCertificateService;
import com.fincity.security.service.SecurityMessageResourceService;

import reactor.core.publisher.Mono;

@RestController
@RequestMapping("api/security/ssl")
public class SSLCertificateController {

    private static final String URL_ID = "urlId";

    private final SecurityMessageResourceService msgService;

    private final SSLCertificateService service;

    public SSLCertificateController(SecurityMessageResourceService msgService, SSLCertificateService service) {
        this.msgService = msgService;
        this.service = service;
    }

    @InitBinder
    public void initBinder(DataBinder binder) {
        binder.registerCustomEditor(ULong.class, new PropertyEditorSupport() {
            @Override
            public void setAsText(String text) {
                if (text == null)
                    setValue(null);
                else
                    setValue(ULong.valueOf(text));
            }
        });
        binder.registerCustomEditor(UInteger.class, new PropertyEditorSupport() {
            @Override
            public void setAsText(String text) {
                if (text == null)
                    setValue(null);
                else
                    setValue(UInteger.valueOf(text));
            }
        });
        binder.registerCustomEditor(UShort.class, new PropertyEditorSupport() {
            @Override
            public void setAsText(String text) {
                if (text == null)
                    setValue(null);
                else
                    setValue(UShort.valueOf(text));
            }
        });
    }

    @PostMapping
    public Mono<ResponseEntity<SSLCertificateOrder>> createCertificateRequest(
            @RequestBody SSLCertificateOrderRequest request) {

        return this.service.createCertificateRequest(request)
                .map(ResponseEntity::ok);
    }

    @PostMapping("external")
    public Mono<ResponseEntity<SSLCertificate>> createExternallyIssuedCertificate(
            @RequestBody SSLCertificate certificate) {

        certificate.setCrtKey(certificate.getCrtKeyUpload());

        return this.service.createExternallyIssuedCertificate(certificate)
                .map(ResponseEntity::ok);
    }

    @GetMapping
    public Mono<ResponseEntity<Page<SSLCertificate>>> readPageFilter(Pageable pageable, ServerHttpRequest request) {
        pageable = (pageable == null ? PageRequest.of(0, 10, Direction.DESC, "createdAt") : pageable);

        HashMap<String, List<String>> params = new HashMap<>(request.getQueryParams());

        ULong urlId = null;

        if (params.containsKey(URL_ID) && !params.get(URL_ID)
                .isEmpty())
            urlId = ULong.valueOf(params.get(URL_ID)
                    .get(0));

        return this.service.findSSLCertificates(urlId, pageable, ConditionUtil.parameterMapToMap(params))
                .map(ResponseEntity::ok);
    }

    @PostMapping("query")
    public Mono<ResponseEntity<Page<SSLCertificate>>> readPageFilter(@RequestBody Query query) {

        Sort sort = query.getSort();

        if (sort == Query.DEFAULT_SORT) {
            sort = Sort.by(Direction.DESC, URL_ID);
        }

        Pageable pageable = PageRequest.of(query.getPage(), query.getSize(), sort);

        return query.getCondition()
                .findConditionWithField(URL_ID)
                .collectList()
                .flatMap(e -> this.service.findSSLCertificates(e.size() != 1 ? null
                                : ULong.valueOf(e.get(0)
                                .getValue()
                                .toString()),
                        pageable, query.getCondition()))
                .switchIfEmpty(Mono.defer(() -> this.service.findSSLCertificates(null, pageable, query.getCondition())))
                .map(ResponseEntity::ok);
    }

    @GetMapping("/certificate")
    public Mono<ResponseEntity<Boolean>> createCertificate(@RequestParam ULong requestId) {

        return this.service.createCertificate(requestId)
                .map(ResponseEntity::ok);
    }

    @GetMapping("/request/challenge")
    public Mono<ResponseEntity<SSLCertificateOrder>> triggerChallenge(@RequestParam ULong challengeId) {

        return this.service.triggerChallenge(challengeId)
                .map(ResponseEntity::ok);
    }

    @GetMapping("/request")
    public Mono<ResponseEntity<SSLCertificateOrder>> readRequestByURLId(@RequestParam ULong urlId) {

        return this.service.readRequestByURLId(urlId)
                .map(ResponseEntity::ok);
    }

    @DeleteMapping("/request")
    public Mono<ResponseEntity<Boolean>> deleteRequestByURLId(@RequestParam ULong urlId) {

        return this.service.deleteRequestByURLId(urlId)
                .map(ResponseEntity::ok);
    }

    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Boolean>> deleteCertificate(@PathVariable("id") String id) {

        return this.service.deleteCertificate(ULong.valueOf(id))
                .map(ResponseEntity::ok);
    }

    @PostMapping("/request/challenges")
    public Mono<ResponseEntity<SSLCertificateOrder>> createChallenges(@RequestParam ULong requestId) {

        return this.service.createChallenges(requestId)
                .map(ResponseEntity::ok);
    }

    @GetMapping("/token/{token}")
    public Mono<ResponseEntity<String>> getToken(@PathVariable String token) {

        return this.service.getToken(token)
                .map(ResponseEntity::ok);
    }

    @GetMapping("/internal/certificates")
    public Mono<ResponseEntity<List<SSLCertificateConfiguration>>> getAllCertificates(
            @RequestHeader(name = "If-None-Match", required = false) String eTag) {

        return FlatMapUtil.flatMapMono(

                SecurityContextUtil::getUsersContextAuthentication,

                ca -> ca.isSystemClient() ? Mono.just(Boolean.TRUE)
                        : this.msgService.throwMessage(
                        msg -> new GenericException(HttpStatus.INTERNAL_SERVER_ERROR, msg),
                        SecurityMessageResourceService.ONLY_SYS_USER_CERTS),

                (ca, validUser) -> this.service.getLastUpdated(),

                (ca, validUser, updatedAt) -> updatedAt.equals(eTag)
                        ? Mono.<ResponseEntity<List<SSLCertificateConfiguration>>>just(
                        ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                                .build())
                        : this.service.getAllCertificates().map(e -> ResponseEntity.ok()
                        .eTag(updatedAt)
                        .body(e)));
    }

}
