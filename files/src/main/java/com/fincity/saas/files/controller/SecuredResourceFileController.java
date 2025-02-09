package com.fincity.saas.files.controller;

import com.fincity.saas.files.model.DownloadOptions;
import com.fincity.saas.files.service.SecuredFileResourceService;

import java.time.temporal.ChronoUnit;

import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("api/files/secured")
public class SecuredResourceFileController extends AbstractResourceFileController<SecuredFileResourceService> {

    public SecuredResourceFileController(SecuredFileResourceService service) {
        super(service);
    }

    @GetMapping("/createKey/**")
    public Mono<ResponseEntity<String>> generateSecureAccess(@RequestParam(required = false) Long timeSpan,
                                                             @RequestParam(required = false) ChronoUnit timeUnit, @RequestParam(required = false) Long accessLimit,
                                                             ServerHttpRequest request) {

        return this.service.createSecuredAccess(timeSpan, timeUnit, accessLimit, request.getPath()
                .toString())
            .map(ResponseEntity::ok);
    }

    @GetMapping("/downloadFileByKey/{key}")
    public Mono<Void> downloadFileWithKey(@PathVariable String key,
                                          @RequestParam(required = false) Integer width,
                                          @RequestParam(required = false) Integer height,
                                          @RequestParam(required = false, defaultValue = "false") Boolean download,
                                          @RequestParam(required = false) DownloadOptions.Fit fit,
                                          @RequestParam(required = false) String background,
                                          @RequestParam(required = false, defaultValue = "auto") DownloadOptions.Gravity gravity,
                                          @RequestParam(required = false, defaultValue = "general") DownloadOptions.Format format,
                                          @RequestParam(required = false, defaultValue = "false") Boolean noCache, ServerHttpRequest request,
                                          @RequestParam(required = false) String name, ServerHttpResponse response
    ) {

        DownloadOptions downloadOptions = new DownloadOptions().setHeight(height)
            .setWidth(width)
            .setFit(fit)
            .setFormat(format)
            .setBackground(background)
            .setGravity(gravity)
            .setNoCache(noCache)
            .setDownload(download)
            .setName(name);

        return this.service.downloadFileByKey(key, downloadOptions, request, response);
    }
}
