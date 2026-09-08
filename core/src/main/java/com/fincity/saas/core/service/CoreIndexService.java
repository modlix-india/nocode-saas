package com.fincity.saas.core.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import com.fincity.saas.commons.core.service.ActionService;
import com.fincity.saas.commons.core.service.ConnectionService;
import com.fincity.saas.commons.core.service.CoreFunctionService;
import com.fincity.saas.commons.core.service.CoreSchemaService;
import com.fincity.saas.commons.core.service.EventActionService;
import com.fincity.saas.commons.core.service.EventDefinitionService;
import com.fincity.saas.commons.core.service.NotificationService;
import com.fincity.saas.commons.core.service.StorageService;
import com.fincity.saas.commons.core.service.TemplateService;
import com.fincity.saas.commons.core.service.WorkflowService;
import com.fincity.saas.commons.mongo.model.ListResultObject;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.util.StringUtil;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * The core-side counterpart to {@code UIIndexService}: every core object an
 * application owns, as name / id / version, in one call.
 *
 * <p>
 * There was no such endpoint, which is why nothing could list an app's objects
 * across ui and core. {@code multi} composes this with the ui index to serve the
 * builder's object tree and search box.
 *
 * <p>
 * The types are declared as a list rather than a {@code Mono.zip} (the shape
 * {@code UIIndexService} uses) for two reasons: zip tops out at eight sources
 * and there are ten here, and adding an eleventh object type should be one line
 * rather than a re-shuffle of tuple indices.
 *
 * <p>
 * Every source reads through {@code readIndexLRO}, so the override chain is
 * resolved to one row per name exactly as the per-type list routes do. Reading
 * on {@code appCode} alone returns one row per client per name instead, which is
 * how the same storage came to appear twice in a tree.
 */
@Service
public class CoreIndexService {

    /** One indexable object type: the key it appears under, and how to read it. */
    private record IndexSource(
            String key,
            BiFunction<String, String, Mono<? extends Page<? extends ListResultObject<?>>>> reader) {
    }

    private final List<IndexSource> sources;

    public CoreIndexService(
            StorageService storageService,
            CoreSchemaService schemaService,
            CoreFunctionService functionService,
            TemplateService templateService,
            ConnectionService connectionService,
            EventDefinitionService eventDefinitionService,
            EventActionService eventActionService,
            NotificationService notificationService,
            WorkflowService workflowService,
            ActionService actionService) {

        this.sources = List.of(
                new IndexSource("storages", storageService::readIndexLRO),
                new IndexSource("schemas", schemaService::readIndexLRO),
                new IndexSource("functions", functionService::readIndexLRO),
                new IndexSource("templates", templateService::readIndexLRO),
                new IndexSource("connections", connectionService::readIndexLRO),
                new IndexSource("eventDefinitions", eventDefinitionService::readIndexLRO),
                new IndexSource("eventActions", eventActionService::readIndexLRO),
                new IndexSource("notifications", notificationService::readIndexLRO),
                new IndexSource("workflows", workflowService::readIndexLRO),
                new IndexSource("actions", actionService::readIndexLRO));
    }

    /**
     * @param clientCode whose view of the app to index. Blank means the caller's
     *                   own client; naming another one is a managed-client read
     *                   and is refused unless the caller manages it.
     */
    public Mono<Map<String, Object>> buildIndex(String appCode, String clientCode) {

        // Every type is read concurrently; the result map is rebuilt in declared
        // order afterwards so the response is stable regardless of who finishes
        // first (a caller rendering a tree should not see groups reorder).
        return Flux.fromIterable(this.sources)
                .flatMap(source -> source.reader().apply(appCode, clientCode)
                        .map(page -> Map.entry(source.key(), summarize(page)))
                        .defaultIfEmpty(Map.entry(source.key(), List.<Map<String, Object>>of())))
                .collectMap(Map.Entry::getKey, Map.Entry::getValue)
                .flatMap(collected -> this.resolvedClientCode(clientCode)
                        .map(forClient -> {
                            Map<String, Object> result = new LinkedHashMap<>();
                            result.put("appCode", appCode);
                            // Which client's view this is, resolved. A caller that
                            // asked for the default has no other way to learn it.
                            result.put("clientCode", forClient);
                            for (IndexSource source : this.sources)
                                result.put(source.key(), collected.getOrDefault(source.key(), List.of()));
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
