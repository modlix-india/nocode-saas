package com.fincity.saas.commons.jooq.flow.schema;

import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.nocode.kirun.engine.json.schema.string.StringFormat;
import com.fincity.nocode.kirun.engine.json.schema.type.SchemaType;
import com.fincity.nocode.kirun.engine.json.schema.type.Type;
import com.fincity.nocode.kirun.engine.namespaces.Namespaces;
import com.fincity.saas.commons.jooq.flow.schema.enums.CommonSqlType;
import com.google.gson.JsonPrimitive;
import java.io.Serial;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import lombok.experimental.FieldNameConstants;

@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@FieldNameConstants
public class ColumnSchema extends AbstractRDBMSSchema {

    public static final Schema COLUMN_SCHEMA = new Schema()
            .setNamespace(Namespaces.SYSTEM)
            .setName("ColumnSchema")
            .setType(Type.of(SchemaType.OBJECT))
            .setProperties(Map.ofEntries(
                    Map.entry(
                            Fields.commonSqlType,
                            Schema.ofString(Fields.commonSqlType).setEnums(CommonSqlType.getCommonSqlType())),
                    Map.entry(
                            Fields.primaryKey,
                            Schema.ofBoolean(Fields.primaryKey).setDefaultValue(new JsonPrimitive(Boolean.FALSE))),
                    Map.entry(
                            Fields.autoIncrement,
                            Schema.ofBoolean(Fields.autoIncrement).setDefaultValue(new JsonPrimitive(Boolean.FALSE))),
                    Map.entry(
                            Fields.unique,
                            Schema.ofBoolean(Fields.unique).setDefaultValue(new JsonPrimitive(Boolean.FALSE))),
                    Map.entry(
                            Fields.indexed,
                            Schema.ofBoolean(Fields.indexed).setDefaultValue(new JsonPrimitive(Boolean.FALSE))),
                    Map.entry(Fields.foreignKey, KeySchema.KEY_SCHEMA),
                    Map.entry(Fields.precision, Schema.ofInteger(Fields.precision)),
                    Map.entry(Fields.scale, Schema.ofInteger(Fields.scale)),
                    Map.entry(Fields.columnDefinition, Schema.ofString(Fields.columnDefinition)),
                    Map.entry(
                            Fields.isNullable,
                            Schema.ofBoolean(Fields.isNullable).setDefaultValue(new JsonPrimitive(Boolean.FALSE))),
                    Map.entry(
                            Fields.isUnsigned,
                            Schema.ofBoolean(Fields.isUnsigned).setDefaultValue(new JsonPrimitive(Boolean.FALSE))),
                    Map.entry(Fields.comment, Schema.ofString(Fields.comment)),
                    Map.entry(Fields.afterColumn, Schema.ofString(Fields.afterColumn))))
            .setRequired(List.of("name", "type", Fields.commonSqlType));

    @Serial
    private static final long serialVersionUID = 4252507002565625625L;

    private String commonSqlType;
    private Boolean primaryKey = Boolean.FALSE;
    private Boolean autoIncrement = Boolean.FALSE;
    private Boolean unique = Boolean.FALSE;
    private Boolean indexed = Boolean.FALSE;
    private KeySchema foreignKey;
    private Integer precision;
    private Integer scale;
    private String columnDefinition;
    private Boolean isNullable = Boolean.FALSE;
    private Boolean isUnsigned = Boolean.FALSE;
    private String comment;
    private String afterColumn;

    public ColumnSchema() {
        super();
    }

    public ColumnSchema(Schema schema) {
        super(schema);
    }

    public static ColumnSchema ofColumn(String columnName, SchemaType schemaType, CommonSqlType commonSqlType) {
        return (ColumnSchema) new ColumnSchema()
                .setCommonSqlType(commonSqlType.getName())
                .setName(columnName)
                .setType(Type.of(schemaType));
    }

    public static ColumnSchema ofChar(String columnName, int length) {
        return (ColumnSchema)
                ofColumn(columnName, SchemaType.STRING, CommonSqlType.CHAR).setMaxLength(length);
    }

    public static ColumnSchema ofVarchar(String columnName, int length) {
        return (ColumnSchema)
                ofColumn(columnName, SchemaType.STRING, CommonSqlType.VARCHAR).setMaxLength(length);
    }

    public static ColumnSchema ofDateTime(String columnName) {
        return (ColumnSchema)
                ofColumn(columnName, SchemaType.STRING, CommonSqlType.TIMESTAMP).setFormat(StringFormat.DATETIME);
    }

    public static ColumnSchema ofDateTime(String columnName, String columnDefinition) {
        return (ColumnSchema) ofColumn(columnName, SchemaType.STRING, CommonSqlType.TIMESTAMP)
                .setColumnDefinition(columnDefinition)
                .setFormat(StringFormat.DATETIME);
    }

    public static ColumnSchema ofDecimal(String columnName, int precision, int scale) {
        return ofColumn(columnName, SchemaType.DOUBLE, CommonSqlType.DECIMAL)
                .setPrecision(precision)
                .setScale(scale);
    }
}
