package com.fincity.security.controller;

import java.beans.PropertyEditorSupport;

import org.jooq.types.ULong;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.DataBinder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.security.dto.Client;
import com.fincity.security.service.ClientManagerService;

import reactor.core.publisher.Mono;

@RestController
@RequestMapping("api/security/client-managers")
public class ClientManagerController {

    private final ClientManagerService service;

    public ClientManagerController(ClientManagerService service) {
        this.service = service;
    }

    @InitBinder
    public void initBinder(DataBinder binder) {
        binder.registerCustomEditor(ULong.class, new PropertyEditorSupport() {
            @Override
            public void setAsText(String text) {
                setValue(text == null ? null : ULong.valueOf(text));
            }
        });
    }

    @GetMapping("/{uid}")
    public Mono<ResponseEntity<Page<Client>>> getClientsOfUser(@PathVariable ULong uid, Pageable pageable) {
        return service.getClientsOfUser(uid, pageable)
                .map(ResponseEntity::ok);
    }

    @PostMapping("/{uid}/{clientId}")
    public Mono<ResponseEntity<Boolean>> create(@PathVariable ULong uid, @PathVariable ULong clientId) {
        return service.create(uid, clientId)
                .map(ResponseEntity::ok);
    }

    @PostMapping("/{clientId}")
    public Mono<ResponseEntity<Boolean>> updateManager(@PathVariable ULong clientId,
            @RequestParam ULong oldManagerId, @RequestParam ULong newManagerId) {
        return service.updateManager(clientId, oldManagerId, newManagerId)
                .map(ResponseEntity::ok);
    }

    @DeleteMapping("/{uid}/{clientId}")
    public Mono<ResponseEntity<Boolean>> delete(@PathVariable ULong uid, @PathVariable ULong clientId) {
        return service.delete(uid, clientId)
                .map(ResponseEntity::ok);
    }
}
