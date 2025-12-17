package com.fincity.saas.entity.processor.service.content;

import com.fincity.nocode.kirun.engine.function.reactive.ReactiveFunction;
import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.nocode.kirun.engine.reactive.ReactiveRepository;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.entity.processor.dao.content.TaskTypeDAO;
import com.fincity.saas.entity.processor.dto.content.TaskType;
import com.fincity.saas.entity.processor.enums.EntitySeries;
import com.fincity.saas.entity.processor.functions.IRepositoryProvider;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorTaskTypesRecord;
import com.fincity.saas.entity.processor.model.common.ProcessorAccess;
import com.fincity.saas.entity.processor.model.request.content.TaskTypeRequest;
import com.fincity.saas.entity.processor.service.ProcessorMessageResourceService;
import com.fincity.saas.entity.processor.service.base.BaseUpdatableService;
import com.fincity.saas.entity.processor.util.ListFunctionRepository;
import com.fincity.saas.entity.processor.util.MapSchemaRepository;
import com.fincity.saas.entity.processor.util.SchemaUtil;
import com.google.gson.Gson;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class TaskTypeService extends BaseUpdatableService<EntityProcessorTaskTypesRecord, TaskType, TaskTypeDAO>
        implements IRepositoryProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(TaskTypeService.class);
    private static final String TASK_TYPE_CACHE = "taskType";

    private final List<ReactiveFunction> functions = new ArrayList<>();
    private final Gson gson;

    @Autowired
    @Lazy
    private TaskTypeService self;

    public TaskTypeService(Gson gson) {
        this.gson = gson;
    }

    @PostConstruct
    private void init() {
        this.functions.addAll(super.getCommonFunctions("TaskType", TaskType.class, gson));
    }

    @Override
    protected String getCacheName() {
        return TASK_TYPE_CACHE;
    }

    @Override
    protected boolean canOutsideCreate() {
        return Boolean.FALSE;
    }

    @Override
    protected Mono<TaskType> checkEntity(TaskType entity, ProcessorAccess access) {
        return super.checkExistsByName(access, entity);
    }

    @Override
    public EntitySeries getEntitySeries() {
        return EntitySeries.TASK_TYPE;
    }

    @Override
    public Mono<TaskType> create(TaskType taskType) {
        return super.hasAccess()
                .flatMap(access -> super.createInternal(access, taskType))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TaskTypeService.create"));
    }

    public Flux<TaskType> createRequests(List<TaskTypeRequest> taskTypeRequests) {

        if (taskTypeRequests == null || taskTypeRequests.isEmpty()) return Flux.empty();

        return super.hasAccess()
                .flatMapMany(access -> {
                    String[] names = taskTypeRequests.stream()
                            .map(TaskTypeRequest::getName)
                            .toArray(String[]::new);

                    return this.existsByName(access.getAppCode(), access.getClientCode(), names)
                            .flatMapMany(exists -> Boolean.TRUE.equals(exists)
                                    ? this.msgService.throwMessage(
                                            msg -> new GenericException(HttpStatus.PRECONDITION_FAILED, msg),
                                            ProcessorMessageResourceService.DUPLICATE_NAME_FOR_ENTITY,
                                            String.join(", ", names),
                                            this.getEntityName())
                                    : Flux.fromIterable(taskTypeRequests)
                                            .flatMap(req -> super.createInternal(access, TaskType.of(req))));
                })
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TaskTypeService.create[List<TaskTypeRequest>]"));
    }

    public Mono<TaskType> createRequest(TaskTypeRequest taskTypeRequest) {
        return super.create(TaskType.of(taskTypeRequest))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TaskTypeService.create[TaskTypeRequest]"));
    }

    private Mono<Boolean> existsByName(String appCode, String clientCode, String... names) {
        return this.dao
                .existsByName(appCode, clientCode, names)
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "TaskTypeService.existsByName"));
    }

    @Override
    public Mono<ReactiveRepository<ReactiveFunction>> getFunctionRepository(String appCode, String clientCode) {
        return Mono.just(new ListFunctionRepository(this.functions));
    }

    @Override
    public Mono<ReactiveRepository<Schema>> getSchemaRepository(
            ReactiveRepository<Schema> staticSchemaRepository, String appCode, String clientCode) {

        Map<String, Schema> schemas = new HashMap<>();
        try {
            Class<?> dtoClass = TaskType.class;
            String namespace = SchemaUtil.getNamespaceForClass(dtoClass);
            String name = dtoClass.getSimpleName();

            Schema schema = SchemaUtil.generateSchemaForClass(dtoClass);
            if (schema != null) {
                schemas.put(namespace + "." + name, schema);
                LOGGER.info("Generated schema for TaskType class: {}.{}", namespace, name);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to generate schema for TaskType class: {}", e.getMessage(), e);
        }

        if (!schemas.isEmpty()) {
            return Mono.just(new MapSchemaRepository(schemas));
        }

        return Mono.empty();
    }
}
