package com.modlix.saas.files.controller;

import java.util.List;

import org.jooq.types.ULong;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

import com.modlix.saas.commons2.security.jwt.ContextAuthentication;
import com.modlix.saas.commons2.security.util.SecurityContextUtil;
import com.modlix.saas.commons2.util.BooleanUtil;
import com.modlix.saas.commons2.util.CommonsUtil;
import com.modlix.saas.commons2.util.FileType;
import com.modlix.saas.files.dto.FilesUploadDownloadDTO;
import com.modlix.saas.files.jooq.enums.FilesUploadDownloadType;
import com.modlix.saas.files.model.DownloadOptions;
import com.modlix.saas.files.model.FileDetail;
import com.modlix.saas.files.service.AbstractFilesResourceService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public abstract class AbstractResourceFileController<T extends AbstractFilesResourceService> {

    protected T service;

    protected AbstractResourceFileController(T service) {
        this.service = service;
    }

    @GetMapping("/**")
    public ResponseEntity<Page<FileDetail>> list(Pageable page, @RequestParam(required = false) String filter,
            @RequestParam(required = false) String clientCode, @RequestParam(required = false) FileType[] fileType,
            HttpServletRequest request) {

        ContextAuthentication ca = SecurityContextUtil.getUsersContextAuthentication();

        return ResponseEntity.ok(this.service.list(
                CommonsUtil.nonNullValue(clientCode, ca.getClientCode(), ca.getLoggedInFromClientCode()),
                request.getRequestURI()
                        .toString(),
                fileType, filter,
                page));
    }

    @DeleteMapping("/**")
    public ResponseEntity<Boolean> delete(@RequestParam(required = false) String clientCode,
            HttpServletRequest request) {

        ContextAuthentication ca = SecurityContextUtil.getUsersContextAuthentication();

        return ResponseEntity.ok(this.service.delete(
                CommonsUtil.nonNullValue(clientCode, ca.getClientCode(), ca.getLoggedInFromClientCode()),
                request.getRequestURI()
                        .toString()));
    }

    @PostMapping("/directory/**")
    public ResponseEntity<FileDetail> createDirectory(@RequestParam(required = false) String clientCode,
            HttpServletRequest request) {

        ContextAuthentication ca = SecurityContextUtil.getUsersContextAuthentication();

        return ResponseEntity.ok(this.service.createDirectory(
                CommonsUtil.nonNullValue(clientCode, ca.getClientCode(), ca.getLoggedInFromClientCode()),
                request.getRequestURI()
                        .toString()));
    }

    @PostMapping("/**")
    public ResponseEntity<Object> create(
            @RequestPart(name = "file", required = false) List<MultipartFile> fileParts,
            @RequestParam(required = false) String clientCode,
            @RequestPart(required = false, name = "override") String override,
            @RequestPart(required = false, name = "name") String fileName, HttpServletRequest request) {

        ContextAuthentication ca = SecurityContextUtil.getUsersContextAuthentication();

        return ResponseEntity.ok(this.service.create(
                CommonsUtil.nonNullValue(clientCode, ca.getClientCode(), ca.getLoggedInFromClientCode()),
                request.getRequestURI()
                        .toString(),
                fileParts, fileName, override != null ? BooleanUtil.safeValueOf(override)
                        : null));
    }

    @GetMapping("/file/**")
    public void downloadFile(@RequestParam(required = false) Integer width,
            @RequestParam(required = false) Integer height,
            @RequestParam(required = false, defaultValue = "false") Boolean download,
            @RequestParam(required = false, defaultValue = "contain") DownloadOptions.Fit fit,
            @RequestParam(required = false) String background,
            @RequestParam(required = false, defaultValue = "auto") DownloadOptions.Gravity gravity,
            @RequestParam(required = false, defaultValue = "general") DownloadOptions.Format format,
            @RequestParam(required = false, defaultValue = "false") Boolean noCache, HttpServletRequest request,
            @RequestParam(required = false) String name, HttpServletResponse response) {

        this.service.downloadFile(new DownloadOptions().setHeight(height)
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
    public ResponseEntity<ULong> createWithZip(
            @RequestPart(name = "file") MultipartFile filePart,
            @RequestParam(required = false) String clientCode,
            @RequestPart(required = false, name = "override") String override, HttpServletRequest request) {

        // Default value is true
        final boolean overrideValue = override == null || BooleanUtil.safeValueOf(override);

        ContextAuthentication ca = SecurityContextUtil.getUsersContextAuthentication();

        return ResponseEntity.ok(this.service.createFromZipFile(
                CommonsUtil.nonNullValue(clientCode, ca.getClientCode(), ca.getLoggedInFromClientCode()),
                request.getRequestURI()
                        .toString(),
                filePart, overrideValue));

    }

    @GetMapping("/export/**")
    public ResponseEntity<ULong> export(@RequestParam(required = false) String clientCode,
            HttpServletRequest request) {

        return ResponseEntity.ok(this.service.asyncExportFolder(clientCode, request));
    }

    @GetMapping("/exports")
    public ResponseEntity<Page<FilesUploadDownloadDTO>> listExports(Pageable page,
            @RequestParam(required = false) String clientCode) {

        return ResponseEntity.ok(this.service.listExportsImports(FilesUploadDownloadType.DOWNLOAD, clientCode, page));
    }

    @GetMapping("/imports")
    public ResponseEntity<Page<FilesUploadDownloadDTO>> listExportsImports(Pageable page,
            @RequestParam(required = false) String clientCode) {

        return ResponseEntity.ok(this.service.listExportsImports(FilesUploadDownloadType.UPLOAD, clientCode, page));
    }
}
