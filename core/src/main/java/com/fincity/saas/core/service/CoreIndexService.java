package com.fincity.saas.core.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.commons.model.condition.FilterConditionOperator;
import com.fincity.saas.commons.model.dto.AbstractOverridableDTO;

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
 */
@Service
public class CoreIndexService {

    private static final Pageable LARGE_PAGE = PageRequest.of(0, 1000);

    /** One indexable object type: the key it appears under, and how to read it. */
    private record IndexSource(
            String key,
            Function<AbstractCondition, Mono<? extends Page<? extends AbstractOverridableDTO<?>>>> reader) {
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
                new IndexSource("storages", c -> storageService.readPageFilter(LARGE_PAGE, c)),
                new IndexSource("schemas", c -> schemaService.readPageFilter(LARGE_PAGE, c)),
                new IndexSource("functions", c -> functionService.readPageFilter(LARGE_PAGE, c)),
                new IndexSource("templates", c -> templateService.readPageFilter(LARGE_PAGE, c)),
                new IndexSource("connections", c -> connectionService.readPageFilter(LARGE_PAGE, c)),
                new IndexSource("eventDefinitions", c -> eventDefinitionService.readPageFilter(LARGE_PAGE, c)),
                new IndexSource("eventActions", c -> eventActionService.readPageFilter(LARGE_PAGE, c)),
                new IndexSource("notifications", c -> notificationService.readPageFilter(LARGE_PAGE, c)),
                new IndexSource("workflows", c -> workflowService.readPageFilter(LARGE_PAGE, c)),
                new IndexSource("actions", c -> actionService.readPageFilter(LARGE_PAGE, c)));
    }

    @SuppressWarnings("java:S1172") // clientCode reserved, mirroring UIIndexService
    public Mono<Map<String, Object>> buildIndex(String appCode, String clientCode) {

        FilterCondition appFilter = new FilterCondition()
                .setField("appCode")
                .setValue(appCode)
                .setOperator(FilterConditionOperator.EQUALS);

        // Every type is read concurrently; the result map is rebuilt in declared
        // order afterwards so the response is stable regardless of who finishes
        // first (a caller rendering a tree should not see groups reorder).
        return Flux.fromIterable(this.sources)
                .flatMap(source -> source.reader().apply(appFilter)
                        .map(page -> Map.entry(source.key(), summarize(page)))
                        .defaultIfEmpty(Map.entry(source.key(), List.<Map<String, Object>>of())))
                .collectMap(Map.Entry::getKey, Map.Entry::getValue)
                .map(collected -> {
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("appCode", appCode);
                    for (IndexSource source : this.sources)
                        result.put(source.key(), collected.getOrDefault(source.key(), List.of()));
                    return result;
                });
    }

    private List<Map<String, Object>> summarize(Page<? extends AbstractOverridableDTO<?>> page) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (AbstractOverridableDTO<?> entity : page.getContent()) {
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("name", entity.getName());
            // The builder tree labels objects by title and falls back to name;
            // name is identity and is not editable, so the index must carry both.
            summary.put("title", entity.getTitle());
            summary.put("id", entity.getId());
            summary.put("version", entity.getVersion());
            summary.put("clientCode", entity.getClientCode());
            items.add(summary);
        }
        return items;
    }
}
