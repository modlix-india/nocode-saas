package com.fincity.saas.ui.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.commons.model.condition.FilterConditionOperator;
import com.fincity.saas.commons.model.dto.AbstractOverridableDTO;

import reactor.core.publisher.Mono;

/**
 * Builds a lightweight application index — all entity names, IDs, versions,
 * and section versions for an appCode in a single call.
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

    private static final Pageable LARGE_PAGE = PageRequest.of(0, 1000);

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

    @SuppressWarnings("java:S1172") // clientCode reserved for future use
    public Mono<Map<String, Object>> buildIndex(String appCode, String clientCode) {

        FilterCondition appFilter = new FilterCondition()
                .setField("appCode")
                .setValue(appCode)
                .setOperator(FilterConditionOperator.EQUALS);

        return Mono.zip(
                applicationService.readPageFilter(LARGE_PAGE, appFilter).defaultIfEmpty(Page.empty()),
                pageService.readPageFilter(LARGE_PAGE, appFilter).defaultIfEmpty(Page.empty()),
                functionService.readPageFilter(LARGE_PAGE, appFilter).defaultIfEmpty(Page.empty()),
                schemaService.readPageFilter(LARGE_PAGE, appFilter).defaultIfEmpty(Page.empty()),
                themeService.readPageFilter(LARGE_PAGE, appFilter).defaultIfEmpty(Page.empty()),
                styleService.readPageFilter(LARGE_PAGE, appFilter).defaultIfEmpty(Page.empty()),
                uriPathService.readPageFilter(LARGE_PAGE, appFilter).defaultIfEmpty(Page.empty())
        ).map(tuple -> {
            Map<String, Object> result = new HashMap<>();
            result.put("appCode", appCode);
            result.put("applications", summarizeList(tuple.getT1()));
            result.put("pages", summarizeList(tuple.getT2()));
            result.put("functions", summarizeList(tuple.getT3()));
            result.put("schemas", summarizeList(tuple.getT4()));
            result.put("themes", summarizeList(tuple.getT5()));
            result.put("styles", summarizeList(tuple.getT6()));
            result.put("uripaths", summarizeList(tuple.getT7()));
            return result;
        });
    }

    private List<Map<String, Object>> summarizeList(Page<? extends AbstractOverridableDTO<?>> page) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (AbstractOverridableDTO<?> entity : page.getContent()) {
            Map<String, Object> summary = new HashMap<>();
            summary.put("name", entity.getName());
            summary.put("id", entity.getId());
            summary.put("version", entity.getVersion());
            // Include per-component versions for pages
            if (entity instanceof com.fincity.saas.ui.document.Page pg) {
                if (pg.getComponentVersions() != null)
                    summary.put("componentVersions", pg.getComponentVersions());
                if (pg.getEventFunctionVersions() != null)
                    summary.put("eventFunctionVersions", pg.getEventFunctionVersions());
            }
            items.add(summary);
        }
        return items;
    }
}
