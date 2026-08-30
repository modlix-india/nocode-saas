package com.fincity.saas.multi.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.multi.fiegn.IFeignCoreService;
import com.fincity.saas.multi.fiegn.IFeignUIService;

import reactor.core.publisher.Mono;
import reactor.util.context.Context;

/**
 * One list of everything an application is made of, across ui and core.
 *
 * <p>
 * This is the composition {@code multi} exists for. Before it, nothing could
 * answer "what objects does this app have": the ui service knew about its seven
 * types, core knew about its ten, and no caller could ask both. The builder's
 * object tree and its search box are the same question, so they are the same
 * call with a filter.
 *
 * <p>
 * The response is a FLAT list rather than a map of typed buckets. A tree groups
 * by type and a search box does not, and flattening once here beats every caller
 * flattening seventeen buckets for itself.
 */
@Service
public class ObjectIndexService {

    private static final Logger logger = LoggerFactory.getLogger(ObjectIndexService.class);

    private final IFeignUIService uiService;
    private final IFeignCoreService coreService;

    /**
     * Keys returned by each service's index, mapped to the singular type name a
     * caller sees. Anything a service returns that is not listed here is ignored:
     * a new object type must be named deliberately, or it silently appears in
     * everyone's tree the day it ships.
     */
    private static final String TITLE = "title";

    private static final Map<String, String> UI_TYPES = Map.of(
            "pages", "page",
            "styles", "style",
            "themes", "theme",
            "functions", "function",
            "schemas", "schema",
            "uripaths", "uripath");

    private static final Map<String, String> CORE_TYPES = Map.ofEntries(
            Map.entry("storages", "storage"),
            Map.entry("schemas", "schema"),
            Map.entry("functions", "function"),
            Map.entry("templates", "template"),
            Map.entry("connections", "connection"),
            Map.entry("eventDefinitions", "eventDefinition"),
            Map.entry("eventActions", "eventAction"),
            Map.entry("notifications", "notification"),
            Map.entry("workflows", "workflow"),
            Map.entry("actions", "action"));

    public ObjectIndexService(IFeignUIService uiService, IFeignCoreService coreService) {
        this.uiService = uiService;
        this.coreService = coreService;
    }

    /**
     * @param query when non-blank, keeps only objects whose name contains it,
     *              case-insensitively. Filtering here rather than in the caller
     *              is the point of the search variant: an app with 4,000 objects
     *              should not ship all of them so a text box can drop most.
     * @param types when non-empty, keeps only these singular type names.
     */
    public Mono<Map<String, Object>> index(
            String authorization, String forwardedHost, String forwardedPort,
            String headerClientCode, String headerAppCode,
            String appCode, String clientCode,
            String query, Set<String> types) {

        return FlatMapUtil.flatMapMono(

                () -> this.uiService.objectIndex(authorization, forwardedHost, forwardedPort,
                        headerClientCode, headerAppCode, appCode, clientCode)
                        .onErrorResume(e -> degrade("ui", appCode, e)),

                uiIndex -> this.coreService.objectIndex(authorization, forwardedHost, forwardedPort,
                        headerClientCode, headerAppCode, appCode, clientCode)
                        .onErrorResume(e -> degrade("core", appCode, e)),

                (uiIndex, coreIndex) -> {

                    List<Map<String, Object>> objects = new ArrayList<>();
                    objects.addAll(flatten(uiIndex, "ui", UI_TYPES));
                    objects.addAll(flatten(coreIndex, "core", CORE_TYPES));

                    List<Map<String, Object>> filtered = filter(objects, query, types);
                    filtered.sort((a, b) -> {
                        int byType = str(a, "type").compareTo(str(b, "type"));
                        return byType != 0 ? byType
                                : str(a, "name").compareToIgnoreCase(str(b, "name"));
                    });

                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("appCode", appCode);
                    result.put("clientCode", clientCode);
                    result.put("count", filtered.size());
                    result.put("counts", countByType(filtered));
                    // The builder's tree splits ui and core into separate groups
                    // (a ui function and a core function are different things to
                    // edit), so counts by type alone cannot label those groups.
                    result.put("countsByServiceType", countByServiceType(filtered));
                    // Which halves answered. A degraded tree must not look like
                    // an app that simply has no storages.
                    result.put("services", Map.of(
                            "ui", !uiIndex.isEmpty(),
                            "core", !coreIndex.isEmpty()));
                    result.put("objects", filtered);
                    return Mono.just(result);
                })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ObjectIndexService.index"));
    }

    /**
     * Half the index is unavailable: log it and carry on with the other half.
     *
     * <p>
     * Degrading beats failing the whole screen, but a degrade that says nothing
     * is indistinguishable from an app with no objects. The caller gets the
     * {@code services} flag; the operator gets this line.
     */
    private Mono<Map<String, Object>> degrade(String service, String appCode, Throwable e) {
        logger.error("Object index: {} service did not answer for app {} - {}: {}",
                service, appCode, e.getClass().getSimpleName(), e.getMessage());
        return Mono.just(Map.of());
    }

    /**
     * One service's typed buckets to flat rows.
     *
     * <p>
     * A service being unreachable yields an empty map upstream rather than an
     * error, so a core outage degrades the tree to its ui half instead of
     * failing the whole screen. The {@code services} entry in the response tells
     * the caller which halves actually answered.
     */
    private List<Map<String, Object>> flatten(
            Map<String, Object> index, String service, Map<String, String> typeKeys) {

        List<Map<String, Object>> out = new ArrayList<>();
        if (index == null || index.isEmpty())
            return out;

        for (Map.Entry<String, String> entry : typeKeys.entrySet()) {

            Object bucket = index.get(entry.getKey());
            if (!(bucket instanceof List<?> list))
                continue;

            for (Object item : list) {
                if (!(item instanceof Map<?, ?> map))
                    continue;
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("service", service);
                row.put("type", entry.getValue());
                row.put("name", map.get("name"));
                // Label for the tree and the tab strip; name stays the identity.
                row.put(TITLE, map.get(TITLE));
                row.put("label", label(map));
                row.put("id", map.get("id"));
                row.put("version", map.get("version"));
                row.put("clientCode", map.get("clientCode"));
                out.add(row);
            }
        }
        return out;
    }

    private List<Map<String, Object>> filter(
            List<Map<String, Object>> objects, String query, Set<String> types) {

        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        Set<String> wanted = types == null ? Set.of() : types;
        boolean hasQuery = !needle.isEmpty();
        if (!hasQuery && wanted.isEmpty())
            return objects;

        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> row : objects) {
            if (!wanted.isEmpty() && !wanted.contains(str(row, "type")))
                continue;
            if (hasQuery && !str(row, "name").toLowerCase(Locale.ROOT).contains(needle))
                continue;
            out.add(row);
        }
        return out;
    }

    /**
     * What the builder shows for an object: its title when it has one, its name
     * otherwise. Resolved here rather than in the client because the UI
     * expression language's nullish operator re-evaluates a result that looks
     * like a path, and object names such as {@code Leads.assignOwner} do.
     */
    private String label(Map<?, ?> map) {
        Object title = map.get(TITLE);
        String titleStr = title == null ? "" : title.toString().trim();
        if (!titleStr.isEmpty())
            return titleStr;
        Object name = map.get("name");
        return name == null ? "" : name.toString();
    }

    private Map<String, Long> countByType(List<Map<String, Object>> objects) {
        return objects.stream().collect(Collectors.groupingBy(
                row -> str(row, "type"), java.util.TreeMap::new, Collectors.counting()));
    }

    /** Keyed {@code <service>_<type>}, the grouping the builder's tree uses. */
    private Map<String, Long> countByServiceType(List<Map<String, Object>> objects) {
        return objects.stream().collect(Collectors.groupingBy(
                row -> str(row, "service") + "_" + str(row, "type"),
                java.util.TreeMap::new, Collectors.counting()));
    }

    private String str(Map<String, Object> row, String key) {
        Object v = row.get(key);
        return v == null ? "" : v.toString();
    }
}
