package com.fincity.security.controller;

import java.beans.PropertyEditorSupport;

import org.jooq.types.UInteger;
import org.jooq.types.ULong;
import org.jooq.types.UShort;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.validation.DataBinder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.security.dto.Profile;
import com.fincity.security.service.ProfileService;

import reactor.core.publisher.Mono;

@RestController
@RequestMapping("api/security/app")
public class ProfileController {

    private final ProfileService service;

    public ProfileController(ProfileService service) {
        this.service = service;
    }

    @InitBinder
    public void initBinder(DataBinder binder) {
        binder.registerCustomEditor(ULong.class, new PropertyEditorSupport() {
            @Override
            public void setAsText(String text) {
                if (text == null)
                    setValue(null);
                setValue(ULong.valueOf(text));
            }
        });
        binder.registerCustomEditor(UInteger.class, new PropertyEditorSupport() {
            @Override
            public void setAsText(String text) {
                if (text == null)
                    setValue(null);
                setValue(UInteger.valueOf(text));
            }
        });
        binder.registerCustomEditor(UShort.class, new PropertyEditorSupport() {
            @Override
            public void setAsText(String text) {
                if (text == null)
                    setValue(null);
                setValue(UShort.valueOf(text));
            }
        });
    }

    @RequestMapping(method = {
            RequestMethod.POST,
            RequestMethod.PUT
    })
    public Mono<ResponseEntity<Profile>> createOrUpdate(@RequestBody Profile entity) {
        return this.service.create(entity)
                .map(ResponseEntity::ok);
    }

    @GetMapping("/profiles/{id}")
    public Mono<ResponseEntity<Profile>> read(
            @PathVariable("id") final ULong id, ServerHttpRequest request) {
        return this.service.read(id)
                .map(ResponseEntity::ok)
                .switchIfEmpty(Mono.defer(() -> Mono.just(ResponseEntity.notFound()
                        .build())));
    }

    @GetMapping("/{appId}/profiles")
    public Mono<ResponseEntity<Page<Profile>>> readPageFilter(@PathVariable("appId") final ULong appId,
            Pageable pageable) {

        pageable = (pageable == null ? PageRequest.of(0, 10, Direction.ASC, "id") : pageable);
        return this.service.readAll(appId, pageable)
                .map(ResponseEntity::ok);
    }

    @DeleteMapping("/profiles/{id}")
    public Mono<ResponseEntity<Boolean>> delete(@PathVariable("id") final ULong id) {
        return this.service.delete(id)
                .map(e -> e > 0)
                .map(ResponseEntity::ok);
    }

    @PostMapping("/profiles/{id}/restrictClient/{clientId}")
    public Mono<ResponseEntity<Boolean>> restrictClient(
            @PathVariable("id") final ULong profileId, @PathVariable("clientId") final ULong clientId) {
        return this.service.restrictClient(profileId, clientId)
                .map(ResponseEntity::ok);
    }
}
