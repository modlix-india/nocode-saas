package com.fincity.saas.core.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincity.nocode.kirun.engine.function.reactive.ReactiveFunction;
import com.fincity.nocode.kirun.engine.reactive.ReactiveHybridRepository;
import com.fincity.nocode.kirun.engine.reactive.ReactiveRepository;
import com.fincity.nocode.kirun.engine.repository.reactive.KIRunReactiveFunctionRepository;
import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.core.document.CoreFunction;
import com.fincity.saas.commons.core.repository.CoreFunctionDocumentRepository;
import com.fincity.saas.commons.core.service.connection.appdata.AppDataService;
import com.fincity.saas.commons.core.service.connection.email.EmailService;
import com.fincity.saas.commons.core.service.file.TemplateConversionService;
import com.fincity.saas.commons.core.service.security.ClientUrlService;
import com.fincity.saas.commons.core.service.security.ContextService;
import com.fincity.saas.commons.mongo.controller.AbstractOverridableDataController;
import com.fincity.saas.commons.mongo.function.DefinitionFunction;
import com.fincity.saas.commons.security.feign.IFeignSecurityService;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.core.feign.IFeignFilesService;
import com.fincity.saas.core.kirun.repository.CoreFunctionRepository;
import com.fincity.saas.core.kirun.repository.CoreFunctionRepository.CoreFunctionRepositoryBuilder;
import com.fincity.saas.core.service.CoreFunctionService;
import com.fincity.saas.core.service.connection.rest.CoreRestService;
import com.google.gson.Gson;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("api/core/functions")
public class FunctionController
        extends AbstractOverridableDataController<CoreFunction, CoreFunctionDocumentRepository, CoreFunctionService> {

    private final CoreFunctionRepository coreFunRepo;
    private final Gson gson;

    public FunctionController(
            AppDataService appDataService,
            ObjectMapper objectMapper,
            CoreRestService restService,
            ContextService userContextService,
            IFeignSecurityService securityService,
            IFeignFilesService fileService,
            ClientUrlService clientUrlService,
            EmailService emailService,
            TemplateConversionService templateConversionService,
            Gson gson) {

        this.coreFunRepo = new CoreFunctionRepository(new CoreFunctionRepositoryBuilder()
                .setAppDataService(appDataService)
                .setObjectMapper(objectMapper)
                .setRestService(restService)
                .setUserContextService(userContextService)
                .setSecurityService(securityService)
                .setFilesService(fileService)
                .setClientUrlService(clientUrlService)
                .setEmailService(emailService)
                .setTemplateConversionService(templateConversionService)
                .setGson(gson));
        this.gson = gson;
    }

    @GetMapping("/repositoryFind")
    public Mono<ResponseEntity<String>> find(
            @RequestParam(required = false) String appCode,
            @RequestParam(required = false) String clientCode,
            @RequestParam(required = false, defaultValue = "false") boolean includeKIRunRepos,
            String namespace,
            String name) {

        return FlatMapUtil.flatMapMono(
                        () -> SecurityContextUtil.resolveAppAndClientCode(appCode, clientCode),
                        ca -> this.service.getFunctionRepository(ca.getT1(), ca.getT2()),
                        (ca, appFunctionRepo) -> {
                            ReactiveRepository<ReactiveFunction> fRepo = (includeKIRunRepos
                                    ? new ReactiveHybridRepository<ReactiveFunction>(
                                            new KIRunReactiveFunctionRepository(), this.coreFunRepo, appFunctionRepo)
                                    : new ReactiveHybridRepository<ReactiveFunction>(
                                            this.coreFunRepo, appFunctionRepo));

                            return fRepo.find(namespace, name)
                                    .map(e -> e instanceof DefinitionFunction definitionFunction
                                            ? definitionFunction.getOnlySignatureFromDefinition()
                                            : e.getSignature());
                        },
                        (ca, appFunctionRepo, signature) ->
                                Mono.just((this.gson).toJson(Map.of("definition", signature))))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "FunctionController.find"))
                .map(str -> ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(str));
    }

    @GetMapping("/repositoryFilter")
    public Mono<ResponseEntity<List<String>>> filter(
            @RequestParam(required = false) String appCode,
            @RequestParam(required = false) String clientCode,
            @RequestParam(required = false, defaultValue = "false") boolean includeKIRunRepos,
            @RequestParam(required = false, defaultValue = "") String filter) {

        return FlatMapUtil.flatMapMono(
                        () -> SecurityContextUtil.resolveAppAndClientCode(appCode, clientCode),
                        (ca) -> this.service.getFunctionRepository(ca.getT1(), ca.getT2()),
                        (ca, appFunctionRepo) -> Mono.just(
                                includeKIRunRepos
                                        ? new ReactiveHybridRepository<ReactiveFunction>(
                                                new KIRunReactiveFunctionRepository(),
                                                this.coreFunRepo,
                                                appFunctionRepo)
                                        : new ReactiveHybridRepository<ReactiveFunction>(
                                                this.coreFunRepo, appFunctionRepo)),
                        (ca, appFunctionRepo, fRepo) -> fRepo.filter(filter).collectList())
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "FunctionController.filter"))
                .map(ResponseEntity::ok);
    }
}
