package com.fincity.saas.ui.controller;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.saas.ui.document.Application;
import com.fincity.saas.ui.document.Page;
import com.fincity.saas.ui.document.UIFunction;
import com.fincity.saas.ui.service.ApplicationService;
import com.fincity.saas.ui.service.EngineService;
import com.fincity.saas.ui.service.PageService;
import com.fincity.saas.ui.service.StyleService;
import com.fincity.saas.ui.service.StyleThemeService;
import com.fincity.saas.ui.service.UIFunctionService;
import com.fincity.saas.ui.utils.ResponseEntityUtils;

import reactor.core.publisher.Mono;

@RestController
@RequestMapping("api/ui/")
public class EngineController {

    private final ApplicationService appService;

    private final PageService pageService;

    private final StyleService styleService;

    private final StyleThemeService themeService;

    private final UIFunctionService functionService;

    private final EngineService engineService;

    public EngineController(ApplicationService appService, PageService pageService, StyleService styleService,
            StyleThemeService themeService, UIFunctionService functionService, EngineService engineService) {
        this.appService = appService;
        this.pageService = pageService;
        this.styleService = styleService;
        this.themeService = themeService;
        this.functionService = functionService;
        this.engineService = engineService;
    }

    @Value("${ui.resourceCacheAge:604800}")
    private int cacheAge;

    private static final ResponseEntity<UIFunction> FUNCTION_NOT_FOUND = ResponseEntity
            .notFound()
            .build();

    @GetMapping("application")
    public Mono<ResponseEntity<Application>> application(@RequestHeader("appCode") String appCode,
            @RequestHeader("clientCode") String clientCode,
            @RequestHeader(name = "If-None-Match", required = false) String eTag) {

        return this.engineService.readApplication(eTag, appCode, clientCode);
    }

    @GetMapping("page/{pageName}")
    public Mono<ResponseEntity<Page>> page(@RequestHeader("appCode") String appCode,
            @RequestHeader("clientCode") String clientCode, @PathVariable("pageName") String pageName,
            @RequestHeader(name = "If-None-Match", required = false) String eTag) {

        return this.engineService.readPage(eTag, pageName, appCode, clientCode);
    }

    @GetMapping(value = "style", produces = { "text/css" })
    public Mono<ResponseEntity<String>> style(@RequestHeader("appCode") String appCode,
            @RequestHeader("clientCode") String clientCode,
            @RequestHeader(name = "If-None-Match", required = false) String eTag) {

        return this.engineService.readStyle(eTag, appCode, clientCode);
    }

    @GetMapping(value = "theme")
    public Mono<ResponseEntity<Map<String, Map<String, String>>>> theme(@RequestHeader("appCode") String appCode,
            @RequestHeader("clientCode") String clientCode,
            @RequestHeader(name = "If-None-Match", required = false) String eTag) {
        return this.engineService.readTheme(eTag, appCode, clientCode);
    }

    @GetMapping("function/{namespace}/{name}")
    public Mono<ResponseEntity<UIFunction>> function(@RequestHeader("appCode") String appCode,
            @RequestHeader("clientCode") String clientCode, @PathVariable("namespace") String namespace,
            @PathVariable("name") String name, @RequestHeader(name = "If-None-Match", required = false) String eTag) {

        return this.functionService.read(namespace + "." + name, appCode, clientCode)
                .flatMap(e -> ResponseEntityUtils.makeResponseEntity(e, eTag, cacheAge))
                .defaultIfEmpty(FUNCTION_NOT_FOUND);
    }

    @GetMapping("urlDetails")
    public Mono<ResponseEntity<Map<String, String>>> urlDetails(@RequestHeader("appCode") String appCode,
            @RequestHeader("clientCode") String clientCode) {

        return Mono.just(ResponseEntity.ok(Map.of("appCode", appCode == null ? "" : appCode,
                "clientCode", clientCode == null ? "" : clientCode)));
    }
}
