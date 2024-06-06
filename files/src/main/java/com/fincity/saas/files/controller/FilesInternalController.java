package com.fincity.saas.files.controller;

import com.fincity.saas.files.model.FileDetail;
import com.fincity.saas.files.service.SecuredFileResourceService;
import com.fincity.saas.files.service.StaticFileResourceService;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("api/files/internal")
public class FilesInternalController {

    private final StaticFileResourceService staticService;
    private final SecuredFileResourceService securedService;

    public FilesInternalController(StaticFileResourceService staticService,
            SecuredFileResourceService securedService) {

        this.staticService = staticService;
        this.securedService = securedService;
    }

    @PostMapping(value = "/{resourceType}")
    public Mono<ResponseEntity<FileDetail>> create(
            @PathVariable String resourceType, @RequestParam String clientCode,
            @RequestParam(required = false, name = "override", defaultValue = "false") boolean override,
            @RequestParam(required = false, defaultValue = "/") String filePath,
            @RequestParam String fileName, ServerHttpRequest request) {

        return ("secured".equals(resourceType) ? this.securedService : this.staticService).createInternal(
                clientCode, override, filePath, fileName, request).map(ResponseEntity::ok);
    }
}
