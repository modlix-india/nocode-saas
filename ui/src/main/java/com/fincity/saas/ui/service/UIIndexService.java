package com.fincity.saas.ui.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import com.fincity.saas.commons.mongo.model.ListResultObject;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.util.StringUtil;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Builds a lightweight application index — all entity names, IDs, versions,
 * and section versions for an appCode in a single call.
 *
 * <p>
 * Reads go through {@code readIndexLRO}, which is the same override resolution
 * the per-type list routes use. Filtering on {@code appCode} alone, as this did
 * originally, returns one row per client per name: an app installed for three
 * clients showed every shared page three times in the builder's tree, inflated
 * the counts by the same factor, and handed the caller the ids of clients
 * outside its own inheritance chain.
 */
@Service
public class UIIndexService {

    private final ApplicationService applicationService;
    private final PageService pageService;
    private final UIFunctionService functionService;
    private final UISchemaService schemaService;
    private final StyleService styleService;
    private final StyleThemeService themeService;
    private final URIPathService uriPathService;

    public UIIndexService(
            ApplicationService applicationService,
            PageService pageService,
            UIFunctionService functionService,
            UISchemaService schemaService,
            StyleService styleService,
            StyleThemeService themeService,
            URIPathService uriPathService) {
        this.applicationService = applicationService;
        this.pageService = pageService;
        this.functionService = functionService;
        this.schemaService = schemaService;
        this.styleService = styleService;
        this.themeService = themeService;
        this.uriPathService = uriPathService;
    }

    public Mono<Map<String, Object>> buildIndex(String appCode, String clientCode) {
        return this.buildIndex(appCode, clientCode, true);
    }

    /**
     * @param clientCode               whose view of the app to index. Blank means
     *                                 the caller's own client; naming another one
     *                                 is a managed-client read and is refused
     *                                 unless the caller manages it.
     *
     * @param includeComponentVersions when false, omit the per-component and
     *                                 per-event version maps from pages.
     *
     *                                 Those maps carry one entry per component
     *                                 per page, and they dominate the response:
     *                                 for the `appbuilder` app the full index is
     *                                 ~273KB, of which roughly 99% is these two
     *                                 maps. Callers that want an inventory (an
     *                                 object tree, a search box, the `multi`
     *                                 cross-service index) never read them, and
     *                                 paying 273KB to learn 99 names made this
     *                                 endpoint more expensive than the
     *                                 per-type list calls it exists to replace.
     *
     *                                 The page editor, which does need them,
     *                                 gets them from the page read.
     */
    public Mono<Map<String, Object>> buildIndex(String appCode, String clientCode,
            boolean includeComponentVersions) {

        return Mono.zip(
                applicationService.readIndexLRO(appCode, clientCode),
                pageService.readIndexLRO(appCode, clientCode),
                functionService.readIndexLRO(appCode, clientCode),
                schemaService.readIndexLRO(appCode, clientCode),
                themeService.readIndexLRO(appCode, clientCode),
                styleService.readIndexLRO(appCode, clientCode),
                uriPathService.readIndexLRO(appCode, clientCode))
                .flatMap(tuple -> Mono.zip(
                        this.pageSummaries(tuple.getT2(), includeComponentVersions),
                        this.resolvedClientCode(clientCode))
                        .map(t -> {
                            Map<String, Object> result = new HashMap<>();
                            result.put("appCode", appCode);
                            // Which client's view this is, resolved. A caller that
                            // asked for the default has no other way to learn it,
                            // and `multi` reports it back to the builder.
                            result.put("clientCode", t.getT2());
                            result.put("applications", summarize(tuple.getT1()));
                            result.put("pages", t.getT1());
                            result.put("functions", summarize(tuple.getT3()));
                            result.put("schemas", summarize(tuple.getT4()));
                            result.put("themes", summarize(tuple.getT5()));
                            result.put("styles", summarize(tuple.getT6()));
                            result.put("uripaths", summarize(tuple.getT7()));
                            return result;
                        }));
    }

    /** The client actually indexed: what was asked for, or the caller's own. */
    private Mono<String> resolvedClientCode(String clientCode) {

        if (!StringUtil.safeIsBlank(clientCode))
            return Mono.just(clientCode);

        return SecurityContextUtil.getUsersContextAuthentication()
                .map(ca -> ca.getClientCode())
                .defaultIfEmpty("");
    }

    /**
     * Pages carry the component and event version maps, which live on the page
     * document rather than on the list row, so asking for them costs one read per
     * page. Sequential and only when asked: the callers that drive a tree pass
     * false and never pay it.
     */
    private Mono<List<Map<String, Object>>> pageSummaries(
            Page<ListResultObject<com.fincity.saas.ui.document.Page>> pages,
            boolean includeComponentVersions) {

        List<Map<String, Object>> items = summarize(pages);

        if (!includeComponentVersions)
            return Mono.just(items);

        return Flux.fromIterable(items)
                .concatMap(item -> pageService.read((String) item.get("id"))
                        .map(pg -> withComponentVersions(item, pg))
                        .defaultIfEmpty(item))
                .collectList();
    }

    private Map<String, Object> withComponentVersions(Map<String, Object> item,
            com.fincity.saas.ui.document.Page pg) {

        if (pg.getComponentVersions() != null)
            item.put("componentVersions", pg.getComponentVersions());
        if (pg.getEventFunctionVersions() != null)
            item.put("eventFunctionVersions", pg.getEventFunctionVersions());
        return item;
    }

    private List<Map<String, Object>> summarize(Page<? extends ListResultObject<?>> page) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (ListResultObject<?> lro : page.getContent()) {
            Map<String, Object> summary = new HashMap<>();
            summary.put("name", lro.getName());
            // The builder tree labels objects by title and falls back to name;
            // name is identity and is not editable, so the index must carry both.
            summary.put("title", lro.getTitle());
            summary.put("id", lro.getId());
            summary.put("version", lro.getVersion());
            // The winning document's client: the caller's own when it has an
            // override, the app owner's otherwise.
            summary.put("clientCode", lro.getClientCode());
            items.add(summary);
        }
        return items;
    }
}
