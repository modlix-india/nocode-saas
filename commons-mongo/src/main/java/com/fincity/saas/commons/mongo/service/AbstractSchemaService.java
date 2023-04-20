package com.fincity.saas.commons.mongo.service;

import static com.fincity.nocode.reactor.util.FlatMapUtil.flatMapMono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;

import com.fincity.nocode.kirun.engine.HybridRepository;
import com.fincity.nocode.kirun.engine.Repository;
import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.nocode.kirun.engine.json.schema.array.ArraySchemaType;
import com.fincity.nocode.kirun.engine.json.schema.array.ArraySchemaType.ArraySchemaTypeAdapter;
import com.fincity.nocode.kirun.engine.json.schema.object.AdditionalType;
import com.fincity.nocode.kirun.engine.json.schema.object.AdditionalType.AdditionalTypeAdapter;
import com.fincity.nocode.kirun.engine.json.schema.type.Type;
import com.fincity.nocode.kirun.engine.json.schema.type.Type.SchemaTypeAdapter;
import com.fincity.nocode.kirun.engine.repository.KIRunSchemaRepository;
import com.fincity.saas.commons.mongo.document.AbstractSchema;
import com.fincity.saas.commons.mongo.repository.IOverridableDataRepository;
import com.fincity.saas.commons.util.StringUtil;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import reactor.core.publisher.Mono;

public abstract class AbstractSchemaService<D extends AbstractSchema<D>, R extends IOverridableDataRepository<D>>
        extends AbstractOverridableDataService<D, R> {

    protected AbstractSchemaService(Class<D> pojoClass) {
        super(pojoClass);
    }

    private static final String CACHE_NAME_SCHEMA_REPO = "cacheSchemaRepo";

    private static final String NAMESPACE = "namespace";
    private static final String NAME = "name";

    private Map<String, Repository<com.fincity.nocode.kirun.engine.json.schema.Schema>> schemas = new HashMap<>();

    @Override
    public Mono<D> create(D entity) {

        String name = StringUtil.safeValueOf(entity.getDefinition()
                .get(NAME));
        String namespace = StringUtil.safeValueOf(entity.getDefinition()
                .get(NAMESPACE));

        if (name == null || namespace == null) {
            return this.messageResourceService.throwMessage(HttpStatus.BAD_REQUEST,
                    AbstractMongoMessageResourceService.NAME_MISSING);
        }

        entity.setName(namespace + "." + name);

        return super.create(entity);
    }

    @Override
    protected Mono<D> updatableEntity(D entity) {

        return flatMapMono(

                () -> this.read(entity.getId()),

                existing -> {
                    if (existing.getVersion() != entity.getVersion())
                        return this.messageResourceService.throwMessage(HttpStatus.PRECONDITION_FAILED,
                                AbstractMongoMessageResourceService.VERSION_MISMATCH);

                    String name = StringUtil.safeValueOf(entity.getDefinition()
                            .get(NAME));
                    String namespace = StringUtil.safeValueOf(entity.getDefinition()
                            .get(NAMESPACE));

                    if (name == null || namespace == null) {
                        return this.messageResourceService.throwMessage(HttpStatus.BAD_REQUEST,
                                AbstractMongoMessageResourceService.NAME_MISSING);
                    }

                    String schemaName = namespace + "." + name;

                    if (!schemaName.equals(existing.getName())) {

                        return this.messageResourceService.throwMessage(HttpStatus.BAD_REQUEST,
                                AbstractMongoMessageResourceService.NAME_CHANGE);
                    }

                    existing.setDefinition(entity.getDefinition());

                    existing.setVersion(existing.getVersion() + 1)
                            .setPermission(entity.getPermission());

                    return Mono.just(existing);
                });
    }

    public Repository<com.fincity.nocode.kirun.engine.json.schema.Schema> getSchemaRepository(String appCode,
            String clientCode) {

        return schemas.computeIfAbsent(appCode + " - " + clientCode, key -> {

            Repository<Schema> repo = new Repository<Schema>() {

                @Override
                public Schema find(String namespace, String name) {

                    return cacheService
                            .cacheValueOrGet(CACHE_NAME_SCHEMA_REPO,
                                    () -> read(namespace + "." + name, appCode, clientCode),
                                    appCode, clientCode, namespace + "." + name)
                            .map(s -> {
                                Gson gson = new GsonBuilder().registerTypeAdapter(Type.class, new SchemaTypeAdapter())
                                        .registerTypeAdapter(AdditionalType.class, new AdditionalTypeAdapter())
                                        .registerTypeAdapter(ArraySchemaType.class,
                                                new ArraySchemaTypeAdapter())
                                        .create();

                                return gson.fromJson(gson.toJsonTree(s.getDefinition()),
                                        Schema.class);
                            }).block();
                }

                @Override
                public List<String> filter(String name) {

                    return List.of();
                }

            };
            return new HybridRepository<>(repo, new KIRunSchemaRepository());
        });
    }
}
