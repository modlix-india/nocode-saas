package com.fincity.saas.ui.service;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.fincity.saas.commons.security.util.SecurityContextUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.model.ObjectWithUniqueID;
import com.fincity.saas.commons.mongo.util.MapWithOrderComparator;
import com.fincity.saas.commons.service.CacheService;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.MergeMapUtil;
import com.fincity.saas.ui.document.Application;
import com.fincity.saas.ui.document.Page;
import com.fincity.saas.ui.utils.ResponseEntityUtils;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class EngineService {

    @Value("${ui.resourceCacheAge:604800}")
    private int cacheAge;

    public static final String CACHE_NAME_APPLICATION = "applicationOUICache";
    public static final String CACHE_NAME_PAGE = "pageOUICache";
    public static final String CACHE_NAME_STYLE = "styleOUICache";
    public static final String CACHE_NAME_THEME = "themeOUICache";

    private final ApplicationService appService;
    private final PageService pageService;
    private final StyleService styleService;
    private final StyleThemeService themeService;

    private final CacheService cacheService;

    private static final ResponseEntity<Application> APPLICATION_NOT_FOUND = ResponseEntity
            .notFound()
            .build();

    private static final ResponseEntity<Page> PAGE_NOT_FOUND = ResponseEntity
            .notFound()
            .build();

    private static final ResponseEntity<String> STYLE_NOT_FOUND = ResponseEntity
            .notFound()
            .build();

    private static final ResponseEntity<Map<String, Map<String, String>>> THEME_NOT_FOUND = ResponseEntity
            .notFound()
            .build();

    public EngineService(ApplicationService appService, PageService pageService, StyleService styleService,
                         StyleThemeService themeService,
                         CacheService cacheService) {
        this.appService = appService;
        this.pageService = pageService;
        this.cacheService = cacheService;
        this.styleService = styleService;
        this.themeService = themeService;
    }

    public Mono<ResponseEntity<Application>> readApplication(String eTag, String appCode, String clientCode) {

        if (eTag == null || eTag.isEmpty()) {
            return this.appService.read(appCode, appCode, clientCode)
                    .flatMap(e -> this.cacheService.put(CACHE_NAME_APPLICATION + "-" + appCode, e, clientCode,
                            e.getUniqueId()))
                    .flatMap(e -> ResponseEntityUtils.makeResponseEntity(e, eTag, cacheAge))
                    .defaultIfEmpty(APPLICATION_NOT_FOUND);
        }

        String uid = eTag.startsWith("W/") ? eTag.substring(2) : eTag;

        return this.cacheService
                .cacheValueOrGet(CACHE_NAME_APPLICATION + "-" + appCode,
                        () -> this.appService.read(appCode, appCode, clientCode), clientCode, uid)
                .flatMap(e -> ResponseEntityUtils.makeResponseEntity(e, uid, cacheAge))
                .defaultIfEmpty(APPLICATION_NOT_FOUND);
    }

    public Mono<ResponseEntity<Page>> readPage(String eTag, String pageName, String appCode, String clientCode) {

        if (eTag == null || eTag.isEmpty()) {

            return FlatMapUtil.flatMapMono(

                            SecurityContextUtil::getUsersContextAuthentication,

                            ca -> this.pageService.read(pageName, appCode, clientCode)
                                    .map(e -> new ObjectWithUniqueID<>(e.getObject(),
                                            (ca.isAuthenticated() ? "lg-" : "nlg-") + e.getUniqueId())),

                            (ca, page) -> this.cacheService.put(CACHE_NAME_PAGE + "-" + appCode, page, clientCode, pageName,
                                    page.getUniqueId()),

                            (ca, page, page2) -> ResponseEntityUtils.makeResponseEntity(page2, eTag, cacheAge))
                    .contextWrite(Context.of(LogUtil.METHOD_NAME, "EngineController.page (eTag Empty)"))
                    .defaultIfEmpty(PAGE_NOT_FOUND);

        }

        String uid = eTag.startsWith("W/") ? eTag.substring(2) : eTag;

        return FlatMapUtil.flatMapMono(
                        SecurityContextUtil::getUsersContextAuthentication,

                        ca -> Mono.just((ca.isAuthenticated() ? "lg-" : "nlg-") + uid.substring(uid.indexOf("-") + 1)),

                        (ca, nUid) -> this.cacheService
                                .cacheValueOrGet(CACHE_NAME_PAGE + "-" + appCode,
                                        () -> this.pageService.read(pageName, appCode, clientCode), clientCode, pageName, nUid)
                                .flatMap(e -> ResponseEntityUtils.makeResponseEntity(e, nUid, cacheAge)))

                .contextWrite(Context.of(LogUtil.METHOD_NAME, "EngineController.page (eTag Not Empty)"))
                .defaultIfEmpty(PAGE_NOT_FOUND);
    }

    public Mono<ResponseEntity<String>> readStyle(String eTag, String appCode, String clientCode) {

        if (eTag == null || eTag.isEmpty()) {
            return this.internalReadStyle(appCode, clientCode)
                    .flatMap(e -> this.cacheService.put(CACHE_NAME_STYLE + "-" + appCode, e, clientCode,
                            e.getUniqueId()))
                    .flatMap(e -> ResponseEntityUtils.makeResponseEntity(e, eTag, cacheAge))
                    .defaultIfEmpty(STYLE_NOT_FOUND);
        }

        String uid = eTag.startsWith("W/") ? eTag.substring(2) : eTag;

        return this.cacheService
                .cacheValueOrGet(CACHE_NAME_STYLE + "-" + appCode,
                        () -> this.internalReadStyle(appCode, clientCode), clientCode, uid)
                .flatMap(e -> ResponseEntityUtils.makeResponseEntity(e, uid, cacheAge))
                .defaultIfEmpty(STYLE_NOT_FOUND);
    }

    @SuppressWarnings("unchecked")
    private Mono<ObjectWithUniqueID<String>> internalReadStyle(String appCode, String clientCode) {

        return FlatMapUtil.flatMapMono(

                        () -> this.appService.read(appCode, appCode, clientCode),

                        appObject -> {

                            var app = appObject.getObject();

                            if (app.getProperties() == null || app.getProperties()
                                    .isEmpty())
                                return Mono.just(List.<String>of());

                            Map<String, Map<String, Object>> styles = (Map<String, Map<String, Object>>) app.getProperties()
                                    .get("styles");

                            if (styles == null || styles.isEmpty())
                                return Mono.just(List.<String>of());

                            return Mono.just(stylesThemesFromProps(styles));
                        },
                        (app, styles) -> {

                            if (styles == null || styles.isEmpty())
                                return Mono.just(new ObjectWithUniqueID<>("", app.getUniqueId()));

                            return Flux.fromIterable(styles)
                                    .flatMap(e -> this.styleService.read(e, appCode, clientCode))
                                    .collectList()
                                    .flatMap(lst -> {
                                        if (lst == null || lst.isEmpty())
                                            return Mono.just(new ObjectWithUniqueID<>("", app.getUniqueId()));

                                        if (lst.size() == 1)
                                            return Mono.just(new ObjectWithUniqueID<>(lst.get(0).getObject().getStyleString(),
                                                    lst.get(0).getUniqueId() + app.getUniqueId()));
                                        StringBuilder finString = new StringBuilder(lst.get(0).getObject().getStyleString());
                                        StringBuilder sb = new StringBuilder();

                                        for (int i = 1; i < lst.size(); i++) {
                                            finString.append("\n");
                                            finString.append(lst.get(i).getObject().getStyleString());
                                            sb.append(lst.get(i).getUniqueId());
                                        }

                                        sb.append(app.getUniqueId());

                                        return Mono.just(new ObjectWithUniqueID<>(finString.toString(), sb.toString()));
                                    })
                                    .defaultIfEmpty(new ObjectWithUniqueID<>("", app.getUniqueId()))
                                    .contextWrite(Context.of(LogUtil.METHOD_NAME, "EngineController.style inner"));
                        })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "EngineController.style"));
    }

    public Mono<ResponseEntity<Map<String, Map<String, String>>>> readTheme(String eTag, String appCode,
                                                                            String clientCode) {

        if (eTag == null || eTag.isEmpty()) {
            return this.internalReadTheme(appCode, clientCode)
                    .flatMap(e -> this.cacheService.put(CACHE_NAME_THEME + "-" + appCode, e, clientCode,
                            e.getUniqueId()))
                    .flatMap(e -> ResponseEntityUtils.makeResponseEntity(e, eTag, cacheAge))
                    .defaultIfEmpty(THEME_NOT_FOUND);
        }

        String uid = eTag.startsWith("W/") ? eTag.substring(2) : eTag;

        return this.cacheService
                .cacheValueOrGet(CACHE_NAME_THEME + "-" + appCode,
                        () -> this.internalReadTheme(appCode, clientCode), clientCode, uid)
                .flatMap(e -> ResponseEntityUtils.makeResponseEntity(e, uid, cacheAge))
                .defaultIfEmpty(THEME_NOT_FOUND);
    }

    @SuppressWarnings("unchecked")
    private Mono<ObjectWithUniqueID<Map<String, Map<String, String>>>> internalReadTheme(String appCode,
                                                                                         String clientCode) {

        return FlatMapUtil.flatMapMono(

                        () -> this.appService.read(appCode, appCode, clientCode),

                        appObject -> {

                            var app = appObject.getObject();

                            if (app.getProperties() == null || app.getProperties()
                                    .isEmpty())
                                return Mono.just(List.<String>of());

                            Map<String, Map<String, Object>> styles = (Map<String, Map<String, Object>>) app.getProperties()
                                    .get("themes");

                            if (styles == null || styles.isEmpty())
                                return Mono.just(List.<String>of());

                            return Mono.just(stylesThemesFromProps(styles));
                        },
                        (app, styles) -> {

                            if (styles == null || styles.isEmpty())
                                return Mono.just(new ObjectWithUniqueID<>(Map.of(), app.getUniqueId()));

                            return Flux.fromIterable(styles)
                                    .flatMap(e -> this.themeService.read(e, appCode, clientCode))
                                    .collectList()
                                    .flatMap(lst -> {
                                        if (lst == null || lst.isEmpty())
                                            return Mono.just(new ObjectWithUniqueID<>(Map.<String, Map<String, String>>of(),
                                                    app.getUniqueId()));

                                        if (lst.size() == 1)
                                            return Mono.just(new ObjectWithUniqueID<>(lst.get(0).getObject().getVariables(),
                                                    lst.get(0).getUniqueId() + app.getUniqueId()));

                                        Map<String, Map<String, String>> finMap = lst.get(0).getObject().getVariables();
                                        StringBuilder sb = new StringBuilder();

                                        for (int i = 1; i < lst.size(); i++) {
                                            finMap = MergeMapUtil.merge(finMap, lst.get(i).getObject().getVariables());
                                            sb.append(lst.get(i).getUniqueId());
                                        }

                                        sb.append(app.getUniqueId());

                                        return Mono.just(new ObjectWithUniqueID<>(finMap, sb.toString()));
                                    })
                                    .defaultIfEmpty(new ObjectWithUniqueID<>(Map.of(), app.getUniqueId()))
                                    .contextWrite(Context.of(LogUtil.METHOD_NAME, "EngineController.theme inner"));
                        })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "EngineController.theme outer"));
    }

    private List<String> stylesThemesFromProps(Map<String, Map<String, Object>> styles) {
        return styles.values()
                .stream()
                .sorted(new MapWithOrderComparator())
                .map(e -> {
                    Object styleName = e.get("name");
                    if (styleName == null)
                        return null;
                    return styleName.toString();
                })
                .filter(Objects::nonNull)
                .toList();
    }
}
