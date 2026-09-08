package com.fincity.saas.core.controller.connection.appdata;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.saas.commons.core.service.connection.appdata.AppDataService;
import com.fincity.saas.commons.model.Query;
import com.fincity.saas.commons.mongo.function.DefinitionFunction;
import com.fincity.saas.commons.util.ConditionUtil;

import reactor.core.publisher.Mono;
import reactor.util.context.Context;

/**
 * Cluster-only, READ-ONLY inspection of storage rows for tooling (the AI
 * service). nginx blocks public {@code /internal/**}.
 *
 * <p>Why this exists: a storage marked {@code onlyThruKIRun} refuses the normal
 * data API outside a KIRun execution, which is correct — pages must reach data
 * through {@code api/core/function/execute/...}. But that also blocks the
 * builder tooling from ever looking at a row to verify what it built. Rather
 * than leave {@code onlyThruKIRun} off (which would let generated pages keep
 * calling the raw data API), tooling gets this route instead.
 *
 * <p>What it relaxes and what it does NOT: it writes
 * {@link DefinitionFunction#CONTEXT_KEY} into the reactor context, the same
 * marker a KIRun execution carries, so {@code onlyThruKIRun} storages become
 * readable. Everything else is untouched — it delegates to the same
 * {@link AppDataService#readPage} the public controller uses, so the caller's
 * security context, the per-storage {@code readAuth} gate and relation
 * resolution all still apply. The caller MUST therefore still present its
 * bearer token; {@code permitAll} on this path only means Spring does not
 * reject the request before the token is read.
 *
 * <p>Deliberately read-only. There is no create/update/delete counterpart:
 * writes must go through KIRun so triggers, validation and events run.
 */
@RestController
@RequestMapping("api/core/internal/data/")
public class InternalAppDataController {

    private static final Set<String> IGNORE_PARAMS = Set.of("page", "size", "sort", "eager", "eagerFields", "count");

    private final AppDataService service;

    public InternalAppDataController(AppDataService service) {
        this.service = service;
    }

    @GetMapping("{storage}")
    public Mono<ResponseEntity<Page<Map<String, Object>>>> readPage(
            @PathVariable("storage") final String storageName,
            @RequestHeader String appCode,
            @RequestHeader String clientCode,
            @RequestParam(value = "count", required = false, defaultValue = "true") Boolean count,
            @RequestParam(required = false, defaultValue = "false") Boolean eager,
            @RequestParam(required = false) List<String> eagerFields,
            Pageable pageable,
            ServerHttpRequest request) {

        pageable = (pageable == null ? PageRequest.of(0, 10, Direction.ASC, "id") : pageable);

        MultiValueMap<String, String> params = request.getQueryParams();
        MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
        for (var param : params.entrySet()) {
            if (IGNORE_PARAMS.contains(param.getKey()))
                continue;
            map.addAll(param.getKey(), param.getValue());
        }

        Query query = new Query().setExcludeFields(false)
                .setFields(List.of())
                .setCondition(ConditionUtil.parameterMapToMap(map))
                .setCount(count)
                .setPage(pageable.getPageNumber())
                .setSize(pageable.getPageSize())
                .setSort(pageable.getSort())
                .setEager(eager)
                .setEagerFields(eagerFields);

        return this.service.readPage(appCode, clientCode, storageName, query)
                .map(ResponseEntity::ok)
                .contextWrite(Context.of(DefinitionFunction.CONTEXT_KEY, "true"));
    }
}
