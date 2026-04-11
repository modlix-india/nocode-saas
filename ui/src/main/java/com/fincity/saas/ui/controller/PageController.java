package com.fincity.saas.ui.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.saas.commons.mongo.controller.AbstractOverridableDataController;
import com.fincity.saas.ui.document.Page;
import com.fincity.saas.ui.model.ComponentDefinition;
import com.fincity.saas.ui.model.ComponentPatchRequest;
import com.fincity.saas.ui.model.EventFunctionPatchRequest;
import com.fincity.saas.ui.repository.PageRepository;
import com.fincity.saas.ui.service.PageService;

import reactor.core.publisher.Mono;

@RestController
@RequestMapping("api/ui/pages")
public class PageController extends AbstractOverridableDataController<Page, PageRepository, PageService> {

    // ── Surgical Page Endpoints ──────────────────────────────────

    /**
     * Read specific components by key from a page's componentDefinition.
     */
    @GetMapping("/{id}/components")
    public Mono<ResponseEntity<Map<String, ComponentDefinition>>> readComponents(
            @PathVariable String id,
            @RequestParam("keys") List<String> keys,
            @RequestParam(value = "depth", defaultValue = "0") int depth) {

        return this.service.read(id)
                .map(page -> {
                    Map<String, ComponentDefinition> compDef = page.getComponentDefinition();
                    if (compDef == null)
                        return ResponseEntity.ok(Map.<String, ComponentDefinition>of());

                    Map<String, ComponentDefinition> result = new java.util.LinkedHashMap<>();
                    for (String key : keys) {
                        ComponentDefinition comp = compDef.get(key);
                        if (comp != null) {
                            result.put(key, comp);
                            if (depth > 0)
                                collectChildren(compDef, comp, result, depth - 1);
                        }
                    }
                    return ResponseEntity.ok(result);
                });
    }

    private void collectChildren(Map<String, ComponentDefinition> compDef,
            ComponentDefinition parent, Map<String, ComponentDefinition> result, int remainingDepth) {
        Map<String, Boolean> children = parent.getChildren();
        if (children == null || remainingDepth < 0)
            return;
        for (Map.Entry<String, Boolean> entry : children.entrySet()) {
            if (Boolean.TRUE.equals(entry.getValue())) {
                ComponentDefinition child = compDef.get(entry.getKey());
                if (child != null && !result.containsKey(entry.getKey())) {
                    result.put(entry.getKey(), child);
                    if (remainingDepth > 0)
                        collectChildren(compDef, child, result, remainingDepth - 1);
                }
            }
        }
    }

    /**
     * Patch a single component with per-component version checking.
     * Concurrent patches to different components on the same page succeed.
     */
    @PatchMapping("/{id}/components/{componentKey}")
    public Mono<ResponseEntity<Page>> patchComponent(
            @PathVariable String id,
            @PathVariable String componentKey,
            @RequestBody ComponentPatchRequest request) {

        return this.service.patchComponent(
                id, componentKey,
                request.getComponentData(),
                request.getExpectedComponentVersion(),
                request.getMessage())
                .map(ResponseEntity::ok);
    }

    /**
     * Read a single event function by name from a page.
     */
    @GetMapping("/{id}/events/{functionName}")
    public Mono<ResponseEntity<Object>> readEvent(
            @PathVariable String id,
            @PathVariable String functionName) {

        return this.service.read(id)
                .map(page -> {
                    Map<String, Object> eventFunctions = page.getEventFunctions();
                    if (eventFunctions == null || !eventFunctions.containsKey(functionName))
                        return ResponseEntity.notFound().<Object>build();
                    return ResponseEntity.ok(eventFunctions.get(functionName));
                });
    }

    /**
     * Write/update a single event function with per-event version checking.
     * Concurrent patches to different event functions on the same page succeed.
     */
    @PutMapping("/{id}/events/{functionName}")
    public Mono<ResponseEntity<Page>> putEvent(
            @PathVariable String id,
            @PathVariable String functionName,
            @RequestBody EventFunctionPatchRequest request) {

        return this.service.patchEventFunction(
                id, functionName,
                request.getDefinition(),
                request.getExpectedEventVersion(),
                request.getMessage())
                .map(ResponseEntity::ok);
    }
}
