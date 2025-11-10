package com.modlix.saas.files.controller;

import org.jooq.types.ULong;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;

import com.modlix.saas.commons2.security.util.LogUtil;
import com.modlix.saas.commons2.security.util.SecurityContextUtil;
import com.modlix.saas.commons2.util.BooleanUtil;
import com.modlix.saas.commons2.util.CommonsUtil;
import com.modlix.saas.commons2.util.FileType;
import com.modlix.saas.files.dto.FilesUploadDownloadDTO;
import com.modlix.saas.files.jooq.enums.FilesUploadDownloadType;
import com.modlix.saas.files.model.DownloadOptions;
import com.modlix.saas.files.model.FileDetail;
import com.modlix.saas.files.service.AbstractFilesResourceService;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

public abstract class AbstractResourceFileController<T extends AbstractFilesResourceService> {

    protected T service;

    protected AbstractResourceFileController(T service) {
        this.service = service;
    }

    @GetMapping("/**")
    public Mono<ResponseEntity<Page<FileDetail>>> list(Pageable page, @RequestParam(required = false) String filter,
            @RequestParam(required = false) String clientCode, @RequestParam(required = false) FileType[] fileType,
            ServerHttpRequest request) {

        return FlatMapUtil.flatMapMono(

                SecurityContextUtil::getUsersContextAuthentication,

                ca -> this.service.list(
                        CommonsUtil.nonNullValue(clientCode, ca.getClientCode(), ca.getLoggedInFromClientCode()),
                        request.getPath()
                                .toString(),
                        fileType, filter,
                        page),

                (ca, pg) -> Mono.just(ResponseEntity.<Page<FileDetail>>ok(pg)))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "AbstractResourceFileController.list"));
    }

    @DeleteMapping("/**")
    public Mono<ResponseEntity<Boolean>> delete(@RequestParam(required = false) String clientCode,
            ServerHttpRequest request) {

        return FlatMapUtil.flatMapMono(

                SecurityContextUtil::getUsersContextAuthentication,

                ca -> this.service.delete(
                        CommonsUtil.nonNullValue(clientCode, ca.getClientCode(), ca.getLoggedInFromClientCode()),
                        request.getPath()
                                .toString()),

                (ca, deleted) -> Mono.just(ResponseEntity.<Boolean>ok(deleted)))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "AbstractResourceFileController.delete"));
    }

    @PostMapping("/directory/**")
    public Mono<ResponseEntity<FileDetail>> createDirectory(@RequestParam(required = false) String clientCode,
            ServerHttpRequest request) {
        return FlatMapUtil.flatMapMonoWithNull(

                SecurityContextUtil::getUsersContextAuthentication,

                ca -> this.service.createDirectory(
                        CommonsUtil.nonNullValue(clientCode, ca.getClientCode(), ca.getLoggedInFromClientCode()),
                        request.getPath()
                                .toString())
                        .map(ResponseEntity::ok))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "AbstractResourceFileController.createDirectory"));
    }

    @PostMapping("/**")
    public Mono<ResponseEntity<Object>> create(
            @RequestPart(name = "file", required = false) Flux<FilePart> fileParts,
            @RequestParam(required = false) String clientCode,
            @RequestPart(required = false, name = "override") String override,
            @RequestPart(required = false, name = "name") String fileName, ServerHttpRequest request) {

        return FlatMapUtil.flatMapMonoWithNull(

                SecurityContextUtil::getUsersContextAuthentication,

                ca -> this.service.create(
                        CommonsUtil.nonNullValue(clientCode, ca.getClientCode(), ca.getLoggedInFromClientCode()),
                        request.getPath()
                                .toString(),
                        fileParts, fileName, override != null ? BooleanUtil.safeValueOf(override)
                                : null))
                .map(ResponseEntity::ok)
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "AbstractResourceFileController.create"));
    }

    @GetMapping("/file/**")
    public Mono<Void> downloadFile(@RequestParam(required = false) Integer width,
            @RequestParam(required = false) Integer height,
            @RequestParam(required = false, defaultValue = "false") Boolean download,
            @RequestParam(required = false, defaultValue = "contain") DownloadOptions.Fit fit,
            @RequestParam(required = false) String background,
            @RequestParam(required = false, defaultValue = "auto") DownloadOptions.Gravity gravity,
            @RequestParam(required = false, defaultValue = "general") DownloadOptions.Format format,
            @RequestParam(required = false, defaultValue = "false") Boolean noCache, ServerHttpRequest request,
            @RequestParam(required = false) String name, ServerHttpResponse response) {

        return service.downloadFile(new DownloadOptions().setHeight(height)
                .setWidth(width)
                .setFit(fit)
                .setFormat(format)
                .setBackground(background)
                .setGravity(gravity)
                .setNoCache(noCache)
                .setDownload(download)
                .setName(name), request, response);
    }

    @PostMapping("/import/**")
    public Mono<ResponseEntity<ULong>> createWithZip(
            @RequestPart(name = "file") Mono<FilePart> filePart,
            @RequestParam(required = false) String clientCode,
            @RequestPart(required = false, name = "override") String override, ServerHttpRequest request) {

        // Default value is true
        final boolean overrideValue = override == null || BooleanUtil.safeValueOf(override);

        return FlatMapUtil.flatMapMonoWithNull(

                SecurityContextUtil::getUsersContextAuthentication,

                ca -> filePart,

                (ca, fp) -> this.service
                        .createFromZipFile(
                                CommonsUtil.nonNullValue(clientCode, ca.getClientCode(),
                                        ca.getLoggedInFromClientCode()),
                                request.getPath()
                                        .toString(),
                                fp, overrideValue))
                .map(ResponseEntity::ok)
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "AbstractResourceFileController.createWithZip"));
    }

    @GetMapping("/export/**")
    public Mono<ResponseEntity<ULong>> export(@RequestParam(required = false) String clientCode,
            ServerHttpRequest request) {

        return this.service.asyncExportFolder(clientCode, request).map(ResponseEntity::ok);
    }

    @GetMapping("/exports")
    public Mono<ResponseEntity<Page<FilesUploadDownloadDTO>>> listExports(Pageable page,
            @RequestParam(required = false) String clientCode) {

        return this.service.listExportsImports(FilesUploadDownloadType.DOWNLOAD, clientCode, page)
                .map(ResponseEntity::ok);
    }

    @GetMapping("/imports")
    public Mono<ResponseEntity<Page<FilesUploadDownloadDTO>>> listExportsImports(Pageable page,
            @RequestParam(required = false) String clientCode) {

        return this.service.listExportsImports(FilesUploadDownloadType.UPLOAD, clientCode, page)
                .map(ResponseEntity::ok);
    }
}
