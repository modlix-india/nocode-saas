package com.fincity.saas.core.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.nocode.kirun.engine.reactive.ReactiveHybridRepository;
import com.fincity.nocode.kirun.engine.reactive.ReactiveRepository;
import com.fincity.nocode.kirun.engine.repository.reactive.KIRunReactiveSchemaRepository;
import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.core.document.CoreSchema;
import com.fincity.saas.commons.core.kirun.repository.CoreSchemaRepository;
import com.fincity.saas.commons.core.repository.CoreSchemaDocumentRepository;
import com.fincity.saas.commons.core.service.CoreSchemaService;
import com.fincity.saas.commons.core.service.RemoteRepositoryService;
import com.fincity.saas.commons.mongo.controller.AbstractOverridableDataController;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.util.LogUtil;

import jakarta.annotation.PostConstruct;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@RestController
@RequestMapping("api/core/schemas")
public class SchemaController
        extends AbstractOverridableDataController<CoreSchema, CoreSchemaDocumentRepository, CoreSchemaService> {

    private CoreSchemaRepository coreSchemaRepo;
    private RemoteRepositoryService remoteRepositoryService;

    public SchemaController(RemoteRepositoryService remoteRepositoryService) {
        this.remoteRepositoryService = remoteRepositoryService;
    }

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
                (ca, appSchemaRepo) -> this.remoteRepositoryService.getRemoteRepositories(ca.getT1(), ca.getT2()),
                (ca, appSchemaRepo, remoteRepositories) -> {
                    ReactiveRepository<Schema> fRepo = (includeKIRunRepos
                            ? new ReactiveHybridRepository<>(
                                    new KIRunReactiveSchemaRepository(), this.coreSchemaRepo, appSchemaRepo)
                            : new ReactiveHybridRepository<>(this.coreSchemaRepo, appSchemaRepo));

                    if (remoteRepositories.isPresent()) {
                        fRepo = new ReactiveHybridRepository<>(
                                remoteRepositories.get().getT2(),
                                fRepo);
                    }

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
                (ca, appSchemaRepo) -> this.remoteRepositoryService.getRemoteRepositories(ca.getT1(), ca.getT2()),
                (ca, appSchemaRepo, remoteRepositories) -> {
                    ReactiveRepository<Schema> fRepo = (includeKIRunRepos
                            ? new ReactiveHybridRepository<>(
                                    new KIRunReactiveSchemaRepository(), this.coreSchemaRepo, appSchemaRepo)
                            : new ReactiveHybridRepository<>(this.coreSchemaRepo, appSchemaRepo));

                    if (remoteRepositories.isPresent()) {
                        fRepo = new ReactiveHybridRepository<>(
                                remoteRepositories.get().getT2(),
                                fRepo);
                    }

                    return Mono.just(fRepo);
                },
                (ca, appSchemaRepo, remoteRepositories, repo) -> repo.filter(filter).collectList())
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "FunctionController.filter"))
                .map(ResponseEntity::ok);
    }
}
