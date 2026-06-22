package com.fincity.saas.commons.core.service;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fincity.nocode.kirun.engine.function.reactive.ReactiveFunction;
import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.nocode.kirun.engine.json.schema.reactive.ReactiveSchemaUtil;
import com.fincity.nocode.kirun.engine.json.schema.type.SchemaType;
import com.fincity.nocode.kirun.engine.json.schema.type.Type;
import com.fincity.nocode.kirun.engine.model.FunctionOutput;
import com.fincity.nocode.kirun.engine.runtime.debug.ExecutionLog;
import com.fincity.nocode.kirun.engine.model.Parameter;
import com.fincity.nocode.kirun.engine.reactive.ReactiveHybridRepository;
import com.fincity.nocode.kirun.engine.reactive.ReactiveRepository;
import com.fincity.nocode.kirun.engine.repository.reactive.KIRunReactiveFunctionRepository;
import com.fincity.nocode.kirun.engine.repository.reactive.KIRunReactiveSchemaRepository;
import com.fincity.nocode.kirun.engine.runtime.reactive.ReactiveFunctionExecutionParameters;
import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.core.document.CoreFunction;
import com.fincity.saas.commons.core.feign.IFeignFilesService;
import com.fincity.saas.commons.core.kirun.repository.CoreFunctionRepository;
import com.fincity.saas.commons.core.kirun.repository.CoreFunctionRepository.CoreFunctionRepositoryBuilder;
import com.fincity.saas.commons.core.kirun.repository.CoreSchemaRepository;
import com.fincity.saas.commons.core.repository.CoreFunctionDocumentRepository;
import com.fincity.saas.commons.core.service.connection.ai.AIService;
import com.fincity.saas.commons.core.service.connection.appdata.AppDataService;
import com.fincity.saas.commons.core.service.connection.email.EmailService;
import com.fincity.saas.commons.core.service.connection.rest.RestService;
import com.fincity.saas.commons.core.service.file.TemplateConversionService;
import com.fincity.saas.commons.core.service.security.ClientUrlService;
import com.fincity.saas.commons.core.service.security.ContextService;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.mongo.function.DefinitionFunction;
import com.fincity.saas.commons.mongo.service.AbstractFunctionService;
import com.fincity.saas.commons.mongo.service.AbstractMongoMessageResourceService;
import com.fincity.saas.commons.mq.events.EventCreationService;
import com.fincity.saas.commons.security.feign.IFeignSecurityService;
import com.fincity.saas.commons.security.service.FeignAuthenticationService;
import com.fincity.saas.commons.security.util.AuthoritiesTokenExtractor;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.StringUtil;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import jakarta.annotation.PostConstruct;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

@Service
public class CoreFunctionService extends AbstractFunctionService<CoreFunction, CoreFunctionDocumentRepository> {

    private static final Logger logger = LoggerFactory.getLogger(CoreFunctionService.class);

    private static final Map<SchemaType, java.util.function.Function<String, Number>> CONVERTOR = Map.of(
            SchemaType.DOUBLE,
            Double::valueOf,
            SchemaType.FLOAT,
            Float::valueOf,
            SchemaType.LONG,
            Long::valueOf,
            SchemaType.INTEGER,
            Integer::valueOf);

    private ReactiveHybridRepository<ReactiveFunction> coreFunctionRepository;

    @Autowired
    @Lazy
    private AppDataService appDataService;

    @Autowired
    @Lazy
    private RestService restService;

    @Autowired
    @Lazy
    private ContextService userContextService;

    @Autowired
    @Lazy
    private CoreSchemaService schemaService;

    @Autowired
    @Lazy
    private IFeignSecurityService feignSecurityService;

    @Autowired
    @Lazy
    private ClientUrlService clientUrlService;

    @Autowired
    @Lazy
    private EmailService emailService;

    @Autowired
    @Lazy
    private TemplateConversionService templateConversionService;

    @Autowired
    @Lazy
    private IFeignFilesService filesService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private AIService aiService;

    @Autowired
    private EventCreationService ecService;

    @Autowired
    private EventDefinitionService edService;

    @Autowired
    private RemoteRepositoryService remoteRepositoryService;

    public CoreFunctionService(FeignAuthenticationService feignAuthenticationService, Gson gson) {
        super(CoreFunction.class, feignAuthenticationService, gson);
    }

    @PostConstruct
    public void init() {
        this.coreFunctionRepository = new ReactiveHybridRepository<>(
                new KIRunReactiveFunctionRepository(),
                new CoreFunctionRepository(new CoreFunctionRepositoryBuilder()
                        .setAppDataService(appDataService)
                        .setRestService(restService)
                        .setUserContextService(userContextService)
                        .setSecurityService(feignSecurityService)
                        .setClientUrlService(clientUrlService)
                        .setEmailService(emailService)
                        .setFilesService(filesService)
                        .setTemplateConversionService(templateConversionService)
                        .setGson(gson)
                        .setObjectMapper(objectMapper)
                        .setNotificationService(notificationService)
                        .setAiService(aiService)
                        .setEcService(ecService)
                        .setEdService(edService)));
    }

    @Override
    public String getObjectName() {
        return "Function";
    }

    public Mono<FunctionOutput> execute(
            String namespace,
            String name,
            String appCode,
            String clientCode,
            Map<String, JsonElement> job,
            ServerHttpRequest request) {
        return FlatMapUtil.flatMapMono(
                SecurityContextUtil::getUsersContextAuthentication,
                ca -> this.getFunctionRepository(appCode, clientCode)
                        .map(appFunctionRepo -> new ReactiveHybridRepository<>(this.coreFunctionRepository,
                                appFunctionRepo)),
                (ca, funRepo) -> this.remoteRepositoryService.getRemoteRepositories(appCode, clientCode),
                (ca, funRepo, remoteRepositories) -> {
                    if (remoteRepositories.isPresent()) {
                        funRepo = new ReactiveHybridRepository<>(
                                remoteRepositories.get().getT1(),
                                funRepo);
                    }
                    return Mono.just(funRepo);
                },
                (ca, funRepo, remoteRepositories, remRepo) -> remRepo.find(namespace, name),
                (ca, funRepo, remoteRepositories, remRepo, fun) -> schemaService
                        .getSchemaRepository(appCode, clientCode)
                        .map(appSchemaRepo -> {
                            if (!remoteRepositories.isPresent()) {
                                return new ReactiveHybridRepository<>(
                                        new KIRunReactiveSchemaRepository(),
                                        new CoreSchemaRepository(),
                                        appSchemaRepo);
                            }
                            return new ReactiveHybridRepository<>(
                                    new KIRunReactiveSchemaRepository(),
                                    new CoreSchemaRepository(),
                                    appSchemaRepo, remoteRepositories.get().getT2());
                        }),
                (ca, funRepo, remoteRepositories, remRepo, fun, schRepo) -> job == null
                        ? getRequestParamsToArguments(fun.getSignature().getParameters(), request, schRepo)
                        : Mono.just(job),
                (ca, funRepo, remoteRepositories, remRepo, fun, schRepo, args) -> {
                    if (fun instanceof DefinitionFunction df
                            && !StringUtil.safeIsBlank(df.getExecutionAuthorization())
                            && !SecurityContextUtil.hasAuthority(
                                    df.getExecutionAuthorization(), ca.getAuthorities()))
                        return this.messageResourceService.throwMessage(
                                msg -> new GenericException(HttpStatus.FORBIDDEN, msg),
                                AbstractMongoMessageResourceService.FORBIDDEN_EXECUTION);

                    AuthoritiesTokenExtractor ate = new AuthoritiesTokenExtractor(ca.getAuthorities());

                    ReactiveRepository<ReactiveFunction> execRepo = new ReactiveHybridRepository<>(
                            new KIRunReactiveFunctionRepository(),
                            this.coreFunctionRepository,
                            remRepo);

                    if (remoteRepositories.isPresent()) {
                        execRepo = new ReactiveHybridRepository<>(
                                remoteRepositories.get().getT1(),
                                execRepo);
                    }

                    Mono<FunctionOutput> result = fun
                            .execute(new ReactiveFunctionExecutionParameters(execRepo, schRepo)
                                    .setArguments(args)
                                    .setValuesMap(Map.of(ate.getPrefix(), ate)));

                    if (fun instanceof DefinitionFunction df) {
                        result = result
                                .doOnNext(output -> logExecutionLogs(df, false))
                                .doOnError(err -> logExecutionLogs(df, true));
                    }

                    return result;
                })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "CoreFunctionService.execute"));
    }

    private void logExecutionLogs(DefinitionFunction df, boolean errored) {
        ExecutionLog execLog = df.getExecutionLog();
        if (execLog == null || execLog.getLogs() == null || execLog.getLogs().isEmpty())
            return;

        String functionName = df.getSignature().getFullName();

        for (var log : execLog.getLogs()) {
            if (errored || log.getError() != null) {
                logger.error("Function: {} | Step: {} | Fn: {} | Duration: {}ms | Event: {} | Error: {}",
                        functionName, log.getStatementName(), log.getFunctionName(),
                        log.getDuration(), log.getEventName(), log.getError());
            } else {
                logger.debug("Function: {} | Step: {} | Fn: {} | Duration: {}ms | Event: {} | Result: {}",
                        functionName, log.getStatementName(), log.getFunctionName(),
                        log.getDuration(), log.getEventName(), log.getResult());
            }
        }
    }

    private Mono<Map<String, JsonElement>> getRequestParamsToArguments(
            Map<String, Parameter> parameters, ServerHttpRequest request, ReactiveRepository<Schema> schemaRepository) {

        MultiValueMap<String, String> queryParams = request == null ? new LinkedMultiValueMap<>()
                : request.getQueryParams();

        return Flux.fromIterable(parameters.entrySet())
                .flatMap(e -> {
                    List<String> value = queryParams.get(e.getKey());

                    if (value == null)
                        return Mono.empty();

                    Schema schema = e.getValue().getSchema();

                    if (!StringUtil.safeIsBlank(schema.getRef()))
                        return ReactiveSchemaUtil.getSchemaFromRef(schema, schemaRepository, schema.getRef())
                                .map(sch -> Tuples.of(e, sch));

                    return Mono.just(Tuples.of(e, schema));
                })
                .flatMap(tup -> {
                    Entry<String, Parameter> e = tup.getT1();
                    Schema schema = tup.getT2();
                    Type type = schema.getType();

                    Parameter param = e.getValue();
                    List<String> value = queryParams.get(e.getKey());

                    if (type.contains(SchemaType.ARRAY) || type.contains(SchemaType.OBJECT))
                        return Mono.empty();

                    if (type.contains(SchemaType.STRING))
                        return Mono.just(jsonElementString(e, value, param));

                    if (type.contains(SchemaType.DOUBLE)) {
                        return Mono.just(jsonElement(e, value, param, SchemaType.DOUBLE));
                    } else if (type.contains(SchemaType.FLOAT)) {
                        return Mono.just(jsonElement(e, value, param, SchemaType.FLOAT));
                    } else if (type.contains(SchemaType.LONG)) {
                        return Mono.just(jsonElement(e, value, param, SchemaType.LONG));
                    } else if (type.contains(SchemaType.INTEGER)) {
                        return Mono.just(jsonElement(e, value, param, SchemaType.INTEGER));
                    }

                    return Mono.empty();
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(Tuple2::getT1, Tuple2::getT2));
    }

    private Tuple2<String, JsonElement> jsonElement(
            Entry<String, Parameter> e, List<String> value, Parameter param, SchemaType type) {
        if (!param.isVariableArgument())
            return Tuples.of(e.getKey(), new JsonPrimitive(CONVERTOR.get(type).apply(value.getFirst())));

        JsonArray jsonArray = new JsonArray();
        value.stream()
                .map(each -> new JsonPrimitive(CONVERTOR.get(type).apply(each)))
                .forEach(jsonArray::add);

        return Tuples.of(e.getKey(), jsonArray);
    }

    private Tuple2<String, JsonElement> jsonElementString(
            Entry<String, Parameter> e, List<String> value, Parameter param) {
        if (!param.isVariableArgument())
            return Tuples.of(e.getKey(), new JsonPrimitive(value.getFirst()));

        JsonArray jsonArray = new JsonArray();
        value.stream().map(JsonPrimitive::new).forEach(jsonArray::add);

        return Tuples.of(e.getKey(), jsonArray);
    }
}
