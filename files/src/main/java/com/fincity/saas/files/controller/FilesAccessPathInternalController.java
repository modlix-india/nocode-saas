package com.fincity.saas.files.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.saas.files.dto.FilesAccessPath;

import reactor.core.publisher.Mono;

@RestController
public class FilesAccessPathInternalController {

    private final com.fincity.saas.files.service.FilesAccessPathService service;

    public FilesAccessPathInternalController(com.fincity.saas.files.service.FilesAccessPathService service) {
        this.service = service;
    }

    @PostMapping("/api/files/internal/accesspath")
    public Mono<ResponseEntity<FilesAccessPath>> createInternalAccessPath(@RequestBody FilesAccessPath accessPath) {
        return service.createInternalAccessPath(accessPath).map(ResponseEntity::ok);
    }
}
