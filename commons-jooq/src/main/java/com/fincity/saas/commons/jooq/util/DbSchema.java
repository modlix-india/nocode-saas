package com.fincity.saas.commons.jooq.util;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.nocode.kirun.engine.json.schema.string.StringFormat;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import lombok.experimental.UtilityClass;

@UtilityClass
public class DbSchema {

    public static final int CODE_LENGTH = 22;
    public static final String DB_NAMESPACE = "Database.Schema";
    private static final Duration ERROR_CORRECTION_DURATION = Duration.ofMinutes(2);

    public static Schema ofString(String id) {
        return Schema.ofString(id).setNamespace(DB_NAMESPACE);
    }

    public static Schema ofNumberId(String id) {
        return Schema.ofLong(id).setNamespace(DB_NAMESPACE).setMinimum(0L);
    }

    public static Schema ofInt(String id) {
        return Schema.ofInteger(id).setNamespace(DB_NAMESPACE);
    }

    public static Schema ofEpochTime(String id) {
        return Schema.ofLong(id)
                .setNamespace(DB_NAMESPACE)
                .setMinimum(LocalDateTime.MIN.toEpochSecond(ZoneOffset.UTC))
                .setMaximum(LocalDateTime.MAX.toEpochSecond(ZoneOffset.UTC));
    }

    public static Schema ofFutureEpochTime(String id) {
        return Schema.ofLong(id)
                .setNamespace(DB_NAMESPACE)
                .setMinimum(LocalDateTime.now().plus(ERROR_CORRECTION_DURATION).toEpochSecond(ZoneOffset.UTC))
                .setMaximum(LocalDateTime.MAX.toEpochSecond(ZoneOffset.UTC));
    }

    public static Schema ofPresentEpochTime(String id) {
        return Schema.ofLong(id)
                .setNamespace(DB_NAMESPACE)
                .setMinimum(LocalDateTime.MIN.toEpochSecond(ZoneOffset.UTC))
                .setMaximum(LocalDateTime.now().plus(ERROR_CORRECTION_DURATION).toEpochSecond(ZoneOffset.UTC));
    }

    public static Schema ofChar(String id) {
        return Schema.ofString(id).setNamespace(DB_NAMESPACE);
    }

    public static Schema ofChar(String id, int length) {
        return Schema.ofString(id)
                .setNamespace(DB_NAMESPACE)
                .setMaxLength(length)
                .setMinLength(0);
    }

    public static Schema ofCharNull(String id, int length) {
        return Schema.ofString(id).setNamespace(DB_NAMESPACE).setMaxLength(length);
    }

    public static Schema ofShortUUID(String id) {
        return Schema.ofString(id)
                .setNamespace(DB_NAMESPACE)
                .setMinimum(CODE_LENGTH)
                .setMaximum(CODE_LENGTH);
    }

    public static Schema ofVersion(String id) {
        return Schema.ofInteger(id).setNamespace(DB_NAMESPACE).setMinimum(0);
    }

	public static Schema ofBoolean(String id) {
		return Schema.ofBoolean(id).setNamespace(DB_NAMESPACE);
	}

    public static Schema ofBooleanFalse(String id) {
        return ofBoolean(id).setDefaultValue(new JsonPrimitive(Boolean.FALSE));
    }

    public static Schema ofBooleanTrue(String id) {
        return ofBoolean(id).setDefaultValue(new JsonPrimitive(Boolean.TRUE));
    }

    public static Schema ofEmail(String id) {
        return Schema.ofString(id).setNamespace(DB_NAMESPACE).setFormat(StringFormat.EMAIL);
    }

    public static Schema ofDialCode(String id) {
        return Schema.ofInteger(id).setNamespace(DB_NAMESPACE).setMinimum(0);
    }

    public static Schema ofPhoneNumber(String id) {
        return Schema.ofString(id).setNamespace(DB_NAMESPACE).setMaxLength(15);
    }

    public static Schema ofAppCode(String id) {
        return Schema.ofString(id).setNamespace(DB_NAMESPACE).setMaxLength(64).setMinLength(0);
    }

    public static Schema ofClientCode(String id) {
        return Schema.ofString(id).setNamespace(DB_NAMESPACE).setMaxLength(8).setMinLength(0);
    }

    public static <E extends Enum<E>> Schema ofEnum(String id, Class<E> enumClass) {
        return Schema.ofString(id).setNamespace(DB_NAMESPACE).setEnums(getSchemaEnums(enumClass));
    }

    public static <E extends Enum<E>> Schema ofEnum(String id, Class<E> enumClass, E defaultEnum) {
        return Schema.ofString(id)
                .setNamespace(DB_NAMESPACE)
                .setEnums(getSchemaEnums(enumClass))
                .setDefaultValue(new JsonPrimitive(defaultEnum.name()));
    }

    public static Schema ofJson(String id) {
        return Schema.ofObject(id).setNamespace(DB_NAMESPACE);
    }

    private static <E extends Enum<E>> List<JsonElement> getSchemaEnums(Class<E> enumClass) {
        if (enumClass == null) throw new IllegalArgumentException("enumClass cannot be null");
        return Arrays.stream(enumClass.getEnumConstants())
                .map(e -> new JsonPrimitive(e.name()))
                .collect(Collectors.toList());
    }
}
