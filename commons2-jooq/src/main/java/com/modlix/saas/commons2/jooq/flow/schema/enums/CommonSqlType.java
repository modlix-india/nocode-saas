package com.modlix.saas.commons2.jooq.flow.schema.enums;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.sql.SQLType;
import java.sql.Types;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public enum CommonSqlType implements SQLType {

    TINYINT(Types.TINYINT),
    SMALLINT(Types.SMALLINT),
    INTEGER(Types.INTEGER),
    BIGINT(Types.BIGINT),
    FLOAT(Types.FLOAT),
    DOUBLE(Types.DOUBLE),
    DECIMAL(Types.DECIMAL),
    CHAR(Types.CHAR),
    VARCHAR(Types.VARCHAR),
    TEXT(Types.LONGVARCHAR),
    DATE(Types.DATE),
    TIME(Types.TIME),
    TIMESTAMP(Types.TIMESTAMP),
    BINARY(Types.BINARY),
    VARBINARY(Types.VARBINARY),
    BLOB(Types.BLOB),
    BOOLEAN(Types.BOOLEAN);

    private final Integer type;

    CommonSqlType(final Integer type) {
        this.type = type;
    }

    @Override
    public String getName() {
        return name();
    }

    @Override
    public String getVendor() {
        return "com.modlix.saas.commons2.jooq";
    }

    @Override
    public Integer getVendorTypeNumber() {
        return type;
    }

    public static List<JsonElement> getCommonSqlType() {
        return Stream.of(CommonSqlType.values())
                .map(sqlType -> new JsonPrimitive(sqlType.name()))
                .collect(Collectors.toList());
    }
}
