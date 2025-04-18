package com.fincity.saas.commons.jooq.convertor.jooq.converters;

import java.time.LocalDateTime;

import org.jooq.impl.AbstractConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fincity.nocode.kirun.engine.json.schema.array.ArraySchemaType;
import com.fincity.nocode.kirun.engine.json.schema.object.AdditionalType;
import com.fincity.nocode.kirun.engine.json.schema.type.Type;
import com.fincity.saas.commons.gson.LocalDateTimeAdapter;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public abstract class AbstractJooqConverter<T, U> extends AbstractConverter<T, U> {

    protected static final Logger logger = LoggerFactory.getLogger(AbstractJooqConverter.class);

    protected final Gson gson = new GsonBuilder()
            .registerTypeAdapter(Type.class, new Type.SchemaTypeAdapter())
            .registerTypeAdapter(AdditionalType.class, new ArraySchemaType.ArraySchemaTypeAdapter())
            .registerTypeAdapter(ArraySchemaType.class, new AdditionalType.AdditionalTypeAdapter())
            .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
            .create();

    protected AbstractJooqConverter(Class<T> fromType, Class<U> toType) {
        super(fromType, toType);
    }

    protected abstract String toData(T databaseObject);

    protected abstract T toJson(String string);

    protected abstract U defaultIfError();

    protected T valueIfNull() {
        return null;
    }

    @Override
    public U from(T databaseObject) {
        if (databaseObject == null) return null;

        try {
            String data = this.toData(databaseObject);
            return gson.fromJson(data, toType());
        } catch (Exception e) {
            logger.error("Error when converting JSON to {}", toType(), e);
            return defaultIfError();
        }
    }

    @Override
    public T to(U userObject) {
        if (userObject == null) return this.valueIfNull();

        try {
            String jsonString = gson.toJson(userObject, toType());
            return this.toJson(jsonString);
        } catch (Exception e) {
            logger.error("Error when converting object of type {} to JSON", toType(), e);
            return this.valueIfNull();
        }
    }
}
