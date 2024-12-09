package com.fincity.saas.files.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.saas.files.model.DownloadOptions;
import com.fincity.saas.files.model.FileDetail;
import com.fincity.saas.files.service.SecuredFileResourceService;
import com.fincity.saas.files.service.StaticFileResourceService;

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
            @PathVariable String resourceType,
            @RequestParam String clientCode,
            @RequestParam(required = false, name = "override", defaultValue = "false") boolean override,
            @RequestParam(required = false, defaultValue = "/") String filePath,
            @RequestParam String fileName, ServerHttpRequest request) {

        return ("secured".equals(resourceType) ? this.securedService : this.staticService)
                .createInternal(clientCode, override, filePath, fileName, request).map(ResponseEntity::ok);
    }

    @GetMapping(value = "/{resourceType}/file")
    public Mono<Void> downloadFile(
            @PathVariable String resourceType,
            @RequestParam String filePath,
            @RequestParam(required = false) Integer width,
            @RequestParam(required = false) Integer height,
            @RequestParam(required = false, defaultValue = "false") Boolean download,
            @RequestParam(required = false, defaultValue = "true") Boolean keepAspectRatio,
            @RequestParam(required = false) String bandColor,
            @RequestParam(required = false, defaultValue = "HORIZONTAL") DownloadOptions.ResizeDirection resizeDirection,
            @RequestParam(required = false, defaultValue = "false") Boolean noCache, ServerHttpRequest request,
            @RequestParam(required = false) String name, ServerHttpResponse response) {

        return ("secured".equals(resourceType) ? this.securedService : this.staticService)
                .readInternal(new DownloadOptions().setHeight(height)
                        .setWidth(width)
                        .setKeepAspectRatio(keepAspectRatio)
                        .setBandColor(bandColor)
                        .setResizeDirection(resizeDirection)
                        .setNoCache(noCache)
                        .setDownload(download)
                        .setName(name), filePath, request, response);
    }

    @GetMapping(value = "/{resourceType}/convertToBase64")
    public Mono<String> convertToBase64(
            @PathVariable String resourceType,
            @RequestParam String clientCode,
            @RequestParam String url,
            @RequestParam(required = false) Boolean metadataRequired) {

        return ("secured".equals(resourceType) ? this.securedService : this.staticService)
                .readFileAsBase64(clientCode, resourceType.toUpperCase(), url, metadataRequired);
    }
}
