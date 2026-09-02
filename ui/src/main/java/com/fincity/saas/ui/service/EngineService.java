package com.fincity.saas.ui.service;

import java.util.ArrayList;
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

    public Mono<ResponseEntity<String>> readStyle(String eTag, String appCode, String clientCode, String theme) {

        return LogUtil.isDraft()
                .flatMap(draft -> this.readStyle(eTag, appCode, clientCode, theme, Boolean.TRUE.equals(draft)));
    }

    private Mono<ResponseEntity<String>> readStyle(String eTag, String appCode, String clientCode, String theme,
            boolean draft) {

        String themeKey = themeCacheKeyPart(theme);

        if (eTag == null || eTag.isEmpty()) {
            return this.internalReadStyle(appCode, clientCode, theme)
                    .map(e -> new ObjectWithUniqueID<>(e.getObject(), draftUid(e.getUniqueId(), draft)))
                    .flatMap(e -> this.cacheService.put(CACHE_NAME_STYLE + "-" + appCode, e, clientCode, themeKey,
                            e.getUniqueId()))
                    .flatMap(e -> draft ? ResponseEntityUtils.makeDraftResponseEntity(e, eTag)
                            : ResponseEntityUtils.makeResponseEntity(e, eTag, cacheAge))
                    .defaultIfEmpty(STYLE_NOT_FOUND);
        }

        String uid = draftUid(eTag.startsWith("W/") ? eTag.substring(2) : eTag, draft);

        return this.cacheService
                .cacheValueOrGet(CACHE_NAME_STYLE + "-" + appCode,
                        () -> this.internalReadStyle(appCode, clientCode, theme), clientCode, themeKey, uid)
                .flatMap(e -> draft ? ResponseEntityUtils.makeDraftResponseEntity(e, uid)
                        : ResponseEntityUtils.makeResponseEntity(e, uid, cacheAge))
                .defaultIfEmpty(STYLE_NOT_FOUND);
    }

    private Mono<ObjectWithUniqueID<String>> internalReadStyle(String appCode, String clientCode, String theme) {

        return FlatMapUtil.flatMapMono(

                        () -> this.appService.read(appCode, appCode, clientCode),

                        appObject -> this.resolveTheme(appObject.getObject(), appCode, clientCode, theme),

                        (appObject, resolved) -> Mono.just(styleNames(appObject.getObject(), resolved.entry())),

                        (app, resolved, styles) -> {

                            // Which theme resolved is part of the answer even when it
                            // contributes no CSS: deleting a theme document shifts
                            // resolution to the next one, whose `style` may differ,
                            // while the app definition's own uniqueId stays put.
                            String themeUid = resolved.uniqueId();

                            if (styles.isEmpty())
                                return Mono.just(new ObjectWithUniqueID<>("", themeUid + app.getUniqueId()));

                            // concatMap, not flatMap. The names arrive sorted by `order`
                            // and later CSS wins the cascade, so an interleaving operator
                            // makes the winning rule depend on which mongo read answered
                            // first. That was the behaviour here until now; it went
                            // unnoticed only because no app has ever had two styles.
                            return Flux.fromIterable(styles)
                                    .concatMap(e -> this.styleService.read(e, appCode, clientCode))
                                    .collectList()
                                    .map(lst -> {
                                        if (lst.isEmpty())
                                            return new ObjectWithUniqueID<>("", themeUid + app.getUniqueId());

                                        StringBuilder finString = new StringBuilder();
                                        StringBuilder sb = new StringBuilder();

                                        for (int i = 0; i < lst.size(); i++) {
                                            if (i > 0)
                                                finString.append("\n");
                                            finString.append(lst.get(i).getObject().getStyleString());
                                            // Every style's uniqueId, including the first.
                                            // It used to start at index 1, so editing the
                                            // first style left the eTag unchanged and
                                            // browsers held the stale sheet for a week.
                                            sb.append(lst.get(i).getUniqueId());
                                        }

                                        sb.append(themeUid)
                                                .append(app.getUniqueId());

                                        return new ObjectWithUniqueID<>(finString.toString(), sb.toString());
                                    })
                                    .contextWrite(Context.of(LogUtil.METHOD_NAME, "EngineService.style inner"));
                        })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "EngineService.style"));
    }

    /**
     * The app-level styles, in `order`, followed by the selected theme's own style
     * if it declares one.
     *
     * The theme's style goes last so it wins the cascade, and it is optional: a
     * theme is perfectly usable as variables alone, which is what every app in
     * production does today. No `style` key means the response is byte for byte
     * what it was before themes became selectable.
     */
    @SuppressWarnings("unchecked")
    private static List<String> styleNames(Application app, Map<String, Object> resolvedTheme) {

        if (app.getProperties() == null || app.getProperties()
                .isEmpty())
            return List.of();

        Map<String, Map<String, Object>> styles = (Map<String, Map<String, Object>>) app.getProperties()
                .get("styles");

        List<String> names = new ArrayList<>(
                styles == null || styles.isEmpty() ? List.of() : stylesThemesFromProps(styles));

        if (resolvedTheme != null) {
            Object themeStyle = resolvedTheme.get("style");
            if (themeStyle != null && !themeStyle.toString()
                    .isBlank())
                names.add(themeStyle.toString());
        }

        return names;
    }

    public Mono<ResponseEntity<Map<String, Map<String, String>>>> readTheme(String eTag, String appCode,
                                                                            String clientCode, String theme) {

        return LogUtil.isDraft()
                .flatMap(draft -> this.readTheme(eTag, appCode, clientCode, theme, Boolean.TRUE.equals(draft)));
    }

    private Mono<ResponseEntity<Map<String, Map<String, String>>>> readTheme(String eTag, String appCode,
            String clientCode, String theme, boolean draft) {

        String themeKey = themeCacheKeyPart(theme);

        if (eTag == null || eTag.isEmpty()) {
            return this.internalReadTheme(appCode, clientCode, theme)
                    .map(e -> new ObjectWithUniqueID<>(e.getObject(), draftUid(e.getUniqueId(), draft)))
                    .flatMap(e -> this.cacheService.put(CACHE_NAME_THEME + "-" + appCode, e, clientCode, themeKey,
                            e.getUniqueId()))
                    .flatMap(e -> draft ? ResponseEntityUtils.makeDraftResponseEntity(e, eTag)
                            : ResponseEntityUtils.makeResponseEntity(e, eTag, cacheAge))
                    .defaultIfEmpty(THEME_NOT_FOUND);
        }

        String uid = draftUid(eTag.startsWith("W/") ? eTag.substring(2) : eTag, draft);

        return this.cacheService
                .cacheValueOrGet(CACHE_NAME_THEME + "-" + appCode,
                        () -> this.internalReadTheme(appCode, clientCode, theme), clientCode, themeKey, uid)
                .flatMap(e -> draft ? ResponseEntityUtils.makeDraftResponseEntity(e, uid)
                        : ResponseEntityUtils.makeResponseEntity(e, uid, cacheAge))
                .defaultIfEmpty(THEME_NOT_FOUND);
    }

    private Mono<ObjectWithUniqueID<Map<String, Map<String, String>>>> internalReadTheme(String appCode,
                                                                                         String clientCode,
                                                                                         String theme) {

        return FlatMapUtil.flatMapMono(

                        () -> this.appService.read(appCode, appCode, clientCode),

                        appObject -> this.resolveTheme(appObject.getObject(), appCode, clientCode, theme),

                        (app, resolved) -> {

                            Map<String, Map<String, String>> variables = resolved.variables();

                            return Mono.just(new ObjectWithUniqueID<>(
                                    variables == null ? Map.<String, Map<String, String>>of() : variables,
                                    resolved.uniqueId() + app.getUniqueId()));
                        })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "EngineService.theme"));
    }

    /**
     * The theme that is actually active: the app-definition entry plus the content
     * of the theme document it names.
     *
     * Both the theme route and the style route go through here, so they cannot
     * disagree about which theme is on. That matters because the two are resolved
     * by separate requests and a mismatch means variables from one theme with the
     * stylesheet of another.
     */
    private record ResolvedTheme(Map<String, Object> entry, String uniqueId,
            Map<String, Map<String, String>> variables) {
    }

    /**
     * The answer when an app has no themes, or none whose document still exists.
     * A sentinel rather than an empty Mono so that every caller gets a concrete
     * type: wrapping this in an Optional inside a FlatMapUtil chain compiles under
     * javac but defeats ECJ's inference, and Eclipse then writes a class whose
     * constructor throws "Unresolved compilation problems" at runtime.
     */
    private static final ResolvedTheme NO_THEME = new ResolvedTheme(null, "", null);

    private Mono<ResolvedTheme> resolveTheme(Application app, String appCode, String clientCode, String requested) {

        List<Map<String, Object>> candidates = themeCandidates(app, requested);

        if (candidates.isEmpty())
            return Mono.just(NO_THEME);

        // concatMap then next(): take the first candidate whose document actually
        // loads. Deleting a theme removes the document but leaves the app
        // definition's entry pointing at it, so "listed" and "exists" are genuinely
        // different questions and the walk has to ask both.
        return Flux.fromIterable(candidates)
                .concatMap(e -> this.themeService.read(e.get("name")
                        .toString(), appCode, clientCode)
                        .map(t -> new ResolvedTheme(e, t.getUniqueId(), t.getObject()
                                .getVariables())))
                .next()
                .defaultIfEmpty(NO_THEME)
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "EngineService.resolveTheme"));
    }

    /**
     * The theme entries to try, most preferred first: the requested one if it is
     * still listed, then every other one in `order`.
     *
     * A requested name that matches nothing is not an error, it falls through to
     * the default. A visitor's stored choice outlives the theme it names, so an
     * author deleting a theme must not leave every visitor holding that cookie
     * with an unthemed app.
     */
    private static List<Map<String, Object>> themeCandidates(Application app, String requested) {

        List<Map<String, Object>> ordered = orderedThemeEntries(app);

        if (requested == null || requested.isBlank() || ordered.isEmpty())
            return ordered;

        List<Map<String, Object>> preferred = ordered.stream()
                .filter(e -> requested.equals(e.get("name")
                        .toString()))
                .toList();

        if (preferred.isEmpty())
            return ordered;

        List<Map<String, Object>> candidates = new ArrayList<>(preferred);
        ordered.stream()
                .filter(e -> !requested.equals(e.get("name")
                        .toString()))
                .forEach(candidates::add);

        return candidates;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> orderedThemeEntries(Application app) {

        if (app.getProperties() == null || app.getProperties()
                .isEmpty())
            return List.of();

        Map<String, Map<String, Object>> themes = (Map<String, Map<String, Object>>) app.getProperties()
                .get("themes");

        if (themes == null || themes.isEmpty())
            return List.of();

        return themes.values()
                .stream()
                .filter(Objects::nonNull)
                .filter(e -> e.get("name") != null && !e.get("name")
                        .toString()
                        .isBlank())
                .sorted(new MapWithOrderComparator())
                .toList();
    }

    /**
     * The selected theme as a cache key part. The requested name is used rather
     * than the resolved one so that the eTag branch does not have to read the app
     * definition just to build a key; the cost is that an explicit request for the
     * default theme gets its own entry holding identical content.
     */
    private static String themeCacheKeyPart(String theme) {
        return theme == null || theme.isBlank() ? "_default" : theme;
    }

    private static List<String> stylesThemesFromProps(Map<String, Map<String, Object>> styles) {
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
