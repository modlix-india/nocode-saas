package com.fincity.saas.core.controller;

import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.nocode.kirun.engine.reactive.ReactiveHybridRepository;
import com.fincity.nocode.kirun.engine.reactive.ReactiveRepository;
import com.fincity.nocode.kirun.engine.repository.reactive.KIRunReactiveSchemaRepository;
import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.core.document.CoreSchema;
import com.fincity.saas.commons.core.kirun.repository.CoreSchemaRepository;
import com.fincity.saas.commons.core.repository.CoreSchemaDocumentRepository;
import com.fincity.saas.commons.core.service.CoreSchemaService;
import com.fincity.saas.commons.mongo.controller.AbstractOverridableDataController;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.util.LogUtil;
import jakarta.annotation.PostConstruct;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.util.List;

@RestController
@RequestMapping("api/core/schemas")
public class SchemaController
        extends AbstractOverridableDataController<CoreSchema, CoreSchemaDocumentRepository, CoreSchemaService> {

    private CoreSchemaRepository coreSchemaRepo;

    @PostConstruct
    public void init() {
        this.coreSchemaRepo = new CoreSchemaRepository();
    }

    @GetMapping("/repositoryFind")
    public Mono<ResponseEntity<Schema>> find(
            @RequestParam(required = false) String appCode,
            @RequestParam(required = false) String clientCode,
            @RequestParam(required = false, defaultValue = "false") boolean includeKIRunRepos,
            String namespace,
            String name) {

        return FlatMapUtil.flatMapMono(
                        () -> SecurityContextUtil.resolveAppAndClientCode(appCode, clientCode),
                        ca -> this.service.getSchemaRepository(ca.getT1(), ca.getT2()),
                        (ca, appSchemaRepo) -> {
                            ReactiveRepository<Schema> fRepo = (includeKIRunRepos
                                    ? new ReactiveHybridRepository<Schema>(
                                            new KIRunReactiveSchemaRepository(), this.coreSchemaRepo, appSchemaRepo)
                                    : new ReactiveHybridRepository<Schema>(this.coreSchemaRepo, appSchemaRepo));

                            return fRepo.find(namespace, name);
                        })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "FunctionController.find"))
                .map(ResponseEntity::ok);
    }

    @GetMapping("/repositoryFilter")
    public Mono<ResponseEntity<List<String>>> filter(
            @RequestParam(required = false) String appCode,
            @RequestParam(required = false) String clientCode,
            @RequestParam(required = false, defaultValue = "false") boolean includeKIRunRepos,
            String filter) {

        return FlatMapUtil.flatMapMono(
                        () -> SecurityContextUtil.resolveAppAndClientCode(appCode, clientCode),
                        ca -> this.service.getSchemaRepository(ca.getT1(), ca.getT2()),
                        (ca, appSchemaRepo) -> Mono.just(
                                includeKIRunRepos
                                        ? new ReactiveHybridRepository<Schema>(
                                                new KIRunReactiveSchemaRepository(), this.coreSchemaRepo, appSchemaRepo)
                                        : new ReactiveHybridRepository<Schema>(this.coreSchemaRepo, appSchemaRepo)),
                        (ca, appSchemaRepo, repo) -> repo.filter(filter).collectList())
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "FunctionController.filter"))
                .map(ResponseEntity::ok);
    }
}
