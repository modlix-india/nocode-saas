package com.fincity.saas.ui.service;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.fincity.saas.commons.security.feign.IFeignSecurityService;
import com.fincity.saas.commons.security.service.FeignAuthenticationService;
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

    private final FeignAuthenticationService securityService;

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
                         StyleThemeService themeService, FeignAuthenticationService securityService,
                         CacheService cacheService) {
        this.appService = appService;
        this.pageService = pageService;
        this.cacheService = cacheService;
        this.styleService = styleService;
        this.themeService = themeService;
        this.securityService = securityService;
    }

    public Mono<ResponseEntity<Application>> readApplication(String eTag, String appCode, String clientCode) {

        return LogUtil.isDraft().flatMap(draft -> this.securityService.getAppStatusByCode(appCode)
                .filter("ACTIVE"::equals)
                .flatMap(x -> this.internalReadApplication(eTag, appCode, clientCode,
                        Boolean.TRUE.equals(draft))));
    }

    private Mono<ResponseEntity<Application>> internalReadApplication(String eTag, String appCode, String clientCode,
            boolean draft) {

        if (eTag == null || eTag.isEmpty()) {
            return this.appService.read(appCode, appCode, clientCode)
                .map(e -> {e.getObject().setUrlClientCode(clientCode); return e;})
                    .map(e -> new ObjectWithUniqueID<>(e.getObject(), draftUid(e.getUniqueId(), draft)))
                    .flatMap(e -> this.cacheService.put(CACHE_NAME_APPLICATION + "-" + appCode, e, clientCode,
                            e.getUniqueId()))
                    .flatMap(e -> draft ? ResponseEntityUtils.makeDraftResponseEntity(e, eTag)
                            : ResponseEntityUtils.makeResponseEntity(e, eTag, cacheAge))
                    .defaultIfEmpty(APPLICATION_NOT_FOUND);
        }

        // Re-derived, never taken from the client as sent. A live eTag replayed
        // against the draft host would otherwise write draft content under the live
        // cache key.
        String uid = draftUid(eTag.startsWith("W/") ? eTag.substring(2) : eTag, draft);

        return this.cacheService
                .cacheValueOrGet(CACHE_NAME_APPLICATION + "-" + appCode,
                        () -> this.appService.read(appCode, appCode, clientCode), clientCode, uid)
                .flatMap(e -> draft ? ResponseEntityUtils.makeDraftResponseEntity(e, uid)
                        : ResponseEntityUtils.makeResponseEntity(e, uid, cacheAge))
                .defaultIfEmpty(APPLICATION_NOT_FOUND);
    }

    /**
     * The ETag already carries an auth dimension, `lg-` when authenticated and
     * `nlg-` when not, so that the two variants of a page never share a cache entry
     * or a browser cache. The draft surface is a third dimension of exactly the same
     * kind, so it prefixes the same marker rather than inventing a parallel scheme.
     *
     * The `d` goes in front, keeping the first `-` where the eTag branch below
     * expects it.
     */
    private static String uniqueIdPrefix(boolean authenticated, boolean draft) {
        return (draft ? "d" : "") + (authenticated ? "lg-" : "nlg-");
    }

    /**
     * The same draft dimension for the reads that have no auth dimension to hang it
     * on: application, style and theme.
     *
     * These three cache under the object's own uniqueId, so without a marker a draft
     * read and a live read of the same object share one cache entry and whichever
     * surface is read first wins for both. That is the identical bug already fixed
     * once in AbstractOverridableDataService.readInternal, one layer up.
     *
     * The separator matters: uniqueIds are base62 (UniqueUtil.BASE) and can
     * themselves begin with 'd', so a bare 'd' prefix would not be reversible.
     * "d-" cannot occur inside one. Idempotent on purpose, so marking an
     * already-marked id is a no-op and unmarking on the live surface always works,
     * which is what stops a client's live eTag from being replayed on the draft host
     * to reach a live cache entry.
     */
    private static final String DRAFT_UID_MARKER = "d-";

    private static String draftUid(String uniqueId, boolean draft) {

        if (uniqueId == null || uniqueId.isEmpty())
            return uniqueId;

        String bare = uniqueId.startsWith(DRAFT_UID_MARKER) ? uniqueId.substring(DRAFT_UID_MARKER.length())
                : uniqueId;

        return draft ? DRAFT_UID_MARKER + bare : bare;
    }

    /**
     * The OUI caches keep one cache name for both surfaces on purpose. Their key
     * includes the uniqueId, which now carries the `d` marker, so a draft entry and
     * a live entry can never collide. Suffixing the cache name as well would mean
     * revisiting all twelve existing evictAll call sites across PageService,
     * StyleService, StyleThemeService, ApplicationService and the ui base class,
     * with a real chance of missing one and leaving a stale draft served after a
     * publish. Sharing the name means every one of them already clears both.
     *
     * The definition cache is different: PageCache_&lt;app&gt;_&lt;name&gt; is keyed by
     * clientCode alone with no uniqueId, so that one does carry the suffix, and
     * evictRecursively clears both explicitly.
     */
    private static String pageCacheName(String appCode) {
        return CACHE_NAME_PAGE + "-" + appCode;
    }

    public Mono<ResponseEntity<Page>> readPage(String eTag, String pageName, String appCode, String clientCode) {

        return LogUtil.isDraft()
                .flatMap(draft -> this.readPage(eTag, pageName, appCode, clientCode, Boolean.TRUE.equals(draft)));
    }

    private Mono<ResponseEntity<Page>> readPage(String eTag, String pageName, String appCode, String clientCode,
            boolean draft) {

        if (eTag == null || eTag.isEmpty()) {

            return FlatMapUtil.flatMapMono(

                            SecurityContextUtil::getUsersContextAuthentication,

                            ca -> this.pageService.read(pageName, appCode, clientCode)
                                    .map(e -> new ObjectWithUniqueID<>(e.getObject(),
                                            uniqueIdPrefix(ca.isAuthenticated(), draft) + e.getUniqueId())),

                            (ca, page) -> this.cacheService.put(pageCacheName(appCode), page, clientCode, pageName,
                                    page.getUniqueId()),

                            (ca, page, page2) -> draft ? ResponseEntityUtils.makeDraftResponseEntity(page2, eTag)
                                    : ResponseEntityUtils.makeResponseEntity(page2, eTag, cacheAge))
                    .contextWrite(Context.of(LogUtil.METHOD_NAME, "EngineController.page (eTag Empty)"))
                    .defaultIfEmpty(PAGE_NOT_FOUND);

        }

        String uid = eTag.startsWith("W/") ? eTag.substring(2) : eTag;

        return FlatMapUtil.flatMapMono(
                        SecurityContextUtil::getUsersContextAuthentication,

                        ca -> Mono.just(uniqueIdPrefix(ca.isAuthenticated(), draft)
                                + uid.substring(uid.indexOf("-") + 1)),

                        (ca, nUid) -> this.cacheService
                                .cacheValueOrGet(pageCacheName(appCode),
                                        () -> this.pageService.read(pageName, appCode, clientCode), clientCode, pageName, nUid)
                                .flatMap(e -> draft ? ResponseEntityUtils.makeDraftResponseEntity(e, nUid)
                                        : ResponseEntityUtils.makeResponseEntity(e, nUid, cacheAge)))

                .contextWrite(Context.of(LogUtil.METHOD_NAME, "EngineController.page (eTag Not Empty)"))
                .defaultIfEmpty(PAGE_NOT_FOUND);
    }

    public Mono<ResponseEntity<String>> readStyle(String eTag, String appCode, String clientCode) {

        return LogUtil.isDraft()
                .flatMap(draft -> this.readStyle(eTag, appCode, clientCode, Boolean.TRUE.equals(draft)));
    }

    private Mono<ResponseEntity<String>> readStyle(String eTag, String appCode, String clientCode, boolean draft) {

        if (eTag == null || eTag.isEmpty()) {
            return this.internalReadStyle(appCode, clientCode)
                    .map(e -> new ObjectWithUniqueID<>(e.getObject(), draftUid(e.getUniqueId(), draft)))
                    .flatMap(e -> this.cacheService.put(CACHE_NAME_STYLE + "-" + appCode, e, clientCode,
                            e.getUniqueId()))
                    .flatMap(e -> draft ? ResponseEntityUtils.makeDraftResponseEntity(e, eTag)
                            : ResponseEntityUtils.makeResponseEntity(e, eTag, cacheAge))
                    .defaultIfEmpty(STYLE_NOT_FOUND);
        }

        String uid = draftUid(eTag.startsWith("W/") ? eTag.substring(2) : eTag, draft);

        return this.cacheService
                .cacheValueOrGet(CACHE_NAME_STYLE + "-" + appCode,
                        () -> this.internalReadStyle(appCode, clientCode), clientCode, uid)
                .flatMap(e -> draft ? ResponseEntityUtils.makeDraftResponseEntity(e, uid)
                        : ResponseEntityUtils.makeResponseEntity(e, uid, cacheAge))
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

        return LogUtil.isDraft()
                .flatMap(draft -> this.readTheme(eTag, appCode, clientCode, Boolean.TRUE.equals(draft)));
    }

    private Mono<ResponseEntity<Map<String, Map<String, String>>>> readTheme(String eTag, String appCode,
            String clientCode, boolean draft) {

        if (eTag == null || eTag.isEmpty()) {
            return this.internalReadTheme(appCode, clientCode)
                    .map(e -> new ObjectWithUniqueID<>(e.getObject(), draftUid(e.getUniqueId(), draft)))
                    .flatMap(e -> this.cacheService.put(CACHE_NAME_THEME + "-" + appCode, e, clientCode,
                            e.getUniqueId()))
                    .flatMap(e -> draft ? ResponseEntityUtils.makeDraftResponseEntity(e, eTag)
                            : ResponseEntityUtils.makeResponseEntity(e, eTag, cacheAge))
                    .defaultIfEmpty(THEME_NOT_FOUND);
        }

        String uid = draftUid(eTag.startsWith("W/") ? eTag.substring(2) : eTag, draft);

        return this.cacheService
                .cacheValueOrGet(CACHE_NAME_THEME + "-" + appCode,
                        () -> this.internalReadTheme(appCode, clientCode), clientCode, uid)
                .flatMap(e -> draft ? ResponseEntityUtils.makeDraftResponseEntity(e, uid)
                        : ResponseEntityUtils.makeResponseEntity(e, uid, cacheAge))
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
