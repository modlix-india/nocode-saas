package com.modlix.saas.files.controller;

import org.jooq.types.UInteger;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.modlix.saas.files.model.DownloadOptions;
import com.modlix.saas.files.model.FileDetail;
import com.modlix.saas.files.service.SecuredFileResourceService;
import com.modlix.saas.files.service.StaticFileResourceService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

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
    public ResponseEntity<FileDetail> create(
            @PathVariable String resourceType,
            @RequestParam String clientCode,
            @RequestParam(required = false, name = "override", defaultValue = "false") boolean override,
            @RequestParam(required = false, defaultValue = "/") String filePath,
            @RequestParam String fileName,
            // How long this file is worth keeping, in minutes from this write. Absent means forever,
            // which is what every existing caller gets and what shared assets must keep getting.
            @RequestParam(required = false) Integer expiresAfterMinutes,
            HttpServletRequest request) {

        return ResponseEntity.ok(("secured".equals(resourceType) ? this.securedService : this.staticService)
                .createInternal(clientCode, override, filePath, fileName,
                        expiresAfterMinutes == null ? null : UInteger.valueOf(expiresAfterMinutes), request));
    }

    /**
     * Removes files whose declared lifetime has passed.
     *
     * <p>Driven by the worker, which is the only thing in this platform that can run a job once
     * across several instances. A {@code @Scheduled} here would fire on every replica at the same
     * moment, and several of them racing to delete the same objects is how a cleanup turns into an
     * outage.
     *
     * <p>Bounded per call and returns what it did, so the worker can decide whether to come back
     * sooner rather than this method deciding how much of the bucket to churn in one go.
     */
    @PostMapping(value = "/{resourceType}/cleanupExpired")
    public ResponseEntity<Integer> cleanupExpired(
            @PathVariable String resourceType,
            @RequestParam(required = false, defaultValue = "500") int limit) {

        return ResponseEntity.ok(("secured".equals(resourceType) ? this.securedService : this.staticService)
                .deleteExpired(limit));
    }

    @GetMapping(value = "/{resourceType}/file")
    public void downloadFile(
            @PathVariable String resourceType,
            @RequestParam String filePath,
            @RequestParam(required = false) Integer width,
            @RequestParam(required = false) Integer height,
            @RequestParam(required = false, defaultValue = "false") Boolean download,
            @RequestParam(required = false, defaultValue = "contain") DownloadOptions.Fit fit,
            @RequestParam(required = false) String background,
            @RequestParam(required = false, defaultValue = "auto") DownloadOptions.Gravity gravity,
            @RequestParam(required = false, defaultValue = "general") DownloadOptions.Format format,
            @RequestParam(required = false, defaultValue = "false") Boolean noCache, HttpServletRequest request,
            @RequestParam(required = false) String name, HttpServletResponse response) {

        ("secured".equals(resourceType) ? this.securedService : this.staticService)
                .readInternal(new DownloadOptions().setHeight(height)
                        .setWidth(width)
                        .setFit(fit)
                        .setFormat(format)
                        .setBackground(background)
                        .setGravity(gravity)
                        .setNoCache(noCache)
                        .setDownload(download)
                        .setName(name), filePath, request, response);
    }

    @GetMapping(value = "/{resourceType}/convertToBase64")
    public ResponseEntity<String> convertToBase64(
            @PathVariable String resourceType,
            @RequestParam String clientCode,
            @RequestParam String url,
            @RequestParam(required = false) Boolean metadataRequired) {

        return ResponseEntity.ok(("secured".equals(resourceType) ? this.securedService : this.staticService)
                .readFileAsBase64(clientCode, url, metadataRequired));
    }

    @PostMapping(value = "/aiUploader", consumes = { "multipart/form-data" })
    public ResponseEntity<String> aiUploader(
            @RequestParam String clientCode,
            @RequestPart(name = "file") MultipartFile file) {

        return ResponseEntity.ok(this.staticService.uploadAiFile(clientCode, file));
    }
}
