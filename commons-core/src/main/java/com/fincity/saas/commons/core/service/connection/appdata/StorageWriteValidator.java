package com.fincity.saas.commons.core.service.connection.appdata;

import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.nocode.kirun.engine.json.schema.validator.reactive.ReactiveSchemaValidator;
import com.fincity.nocode.kirun.engine.reactive.ReactiveHybridRepository;
import com.fincity.nocode.kirun.engine.reactive.ReactiveRepository;
import com.fincity.nocode.kirun.engine.repository.reactive.KIRunReactiveSchemaRepository;
import com.fincity.saas.commons.core.document.Storage;
import com.fincity.saas.commons.core.kirun.repository.CoreSchemaRepository;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import reactor.core.publisher.Mono;

/**
 * Validates a row against its storage's schema on the way in.
 *
 * Shared deliberately. Every {@link IAppDataService} backend has to apply the same
 * validation or the same storage definition would mean different things depending on
 * which engine a tenant happens to sit on, and that divergence would be invisible until
 * a tenant moved. This lived as a private method on the Mongo service until a second
 * backend needed it.
 */
@Component
public class StorageWriteValidator {

    private final Gson gson;

    public StorageWriteValidator(Gson gson) {
        this.gson = gson;
    }

    /**
     * Relation fields are lifted out before validation and put back afterwards.
     *
     * They hold ids pointing at other storages rather than values the row's own schema
     * describes, so validating them against it would reject every row that has one.
     */
    public Mono<JsonObject> validate(
            Map<String, Object> objectMap, Storage storage, Schema schema, ReactiveRepository<Schema> appSchemaRepo) {

        JsonObject job = this.gson.toJsonTree(objectMap).getAsJsonObject();

        Map<String, JsonElement> relations = new HashMap<>();
        if (storage.getRelations() != null && !storage.getRelations().isEmpty())
            storage.getRelations().forEach((key, relation) -> {
                if (job.has(key)) relations.put(key, job.remove(key));
            });

        return ReactiveSchemaValidator.validate(
                        null,
                        schema,
                        new ReactiveHybridRepository<>(
                                new KIRunReactiveSchemaRepository(), new CoreSchemaRepository(), appSchemaRepo),
                        job)
                .map(JsonElement::getAsJsonObject)
                .map(validatedJsonObject -> {
                    relations.forEach(validatedJsonObject::add);
                    return validatedJsonObject;
                });
    }
}
