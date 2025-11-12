package com.modlix.saas.files.controller;

import java.time.temporal.ChronoUnit;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.modlix.saas.files.model.DownloadOptions;
import com.modlix.saas.files.service.SecuredFileResourceService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("api/files/secured")
public class SecuredResourceFileController extends AbstractResourceFileController<SecuredFileResourceService> {

    public SecuredResourceFileController(SecuredFileResourceService service) {
        super(service);
    }

    @GetMapping("/createKey/**")
    public ResponseEntity<String> generateSecureAccess(@RequestParam(required = false) Long timeSpan,
            @RequestParam(required = false) ChronoUnit timeUnit, @RequestParam(required = false) Long accessLimit,
            HttpServletRequest request) {

        return ResponseEntity
                .ok(this.service.createSecuredAccess(timeSpan, timeUnit, accessLimit, request.getRequestURI()));
    }

    @GetMapping("/downloadFileByKey/{key}")
    public void downloadFileWithKey(@PathVariable String key,
            @RequestParam(required = false) Integer width,
            @RequestParam(required = false) Integer height,
            @RequestParam(required = false, defaultValue = "false") Boolean download,
            @RequestParam(required = false, defaultValue = "contain") DownloadOptions.Fit fit,
            @RequestParam(required = false) String background,
            @RequestParam(required = false, defaultValue = "auto") DownloadOptions.Gravity gravity,
            @RequestParam(required = false, defaultValue = "general") DownloadOptions.Format format,
            @RequestParam(required = false, defaultValue = "false") Boolean noCache, HttpServletRequest request,
            @RequestParam(required = false) String name, HttpServletResponse response) {

        DownloadOptions downloadOptions = new DownloadOptions().setHeight(height)
                .setWidth(width)
                .setFit(fit)
                .setFormat(format)
                .setBackground(background)
                .setGravity(gravity)
                .setNoCache(noCache)
                .setDownload(download)
                .setName(name);

        this.service.downloadFileByKey(key, downloadOptions, request, response);
    }
}
