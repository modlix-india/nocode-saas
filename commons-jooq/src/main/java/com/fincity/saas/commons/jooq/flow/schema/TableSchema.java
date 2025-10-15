package com.fincity.saas.commons.jooq.flow.schema;

import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.nocode.kirun.engine.json.schema.type.SchemaType;
import com.fincity.nocode.kirun.engine.json.schema.type.Type;
import com.fincity.nocode.kirun.engine.namespaces.Namespaces;
import com.fincity.saas.commons.jooq.flow.schema.enums.KeyType;
import com.google.gson.JsonPrimitive;
import java.io.Serial;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.FieldNameConstants;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@FieldNameConstants
public class TableSchema extends AbstractRDBMSSchema {

    public static final Schema TABLE_SCHEMA = new Schema()
            .setNamespace(Namespaces.SYSTEM)
            .setName("TableSchema")
            .setType(Type.of(SchemaType.OBJECT))
            .setProperties(Map.ofEntries(
                    Map.entry(Fields.tableName, Schema.ofString(Fields.tableName)),
                    Map.entry(Fields.tableDescription, Schema.ofString(Fields.tableDescription)),
                    Map.entry(
                            Fields.columns,
                            Schema.ofObject(Fields.columns).setAllOf(List.of(ColumnSchema.COLUMN_SCHEMA))),
                    Map.entry(Fields.keys, Schema.ofArray(Fields.keys, KeySchema.KEY_SCHEMA)),
                    Map.entry(
                            Fields.isAudited,
                            Schema.ofBoolean(Fields.isAudited).setDefaultValue(new JsonPrimitive(Boolean.FALSE))),
                    Map.entry(
                            Fields.isSoftDelete,
                            Schema.ofBoolean(Fields.isSoftDelete).setDefaultValue(new JsonPrimitive(Boolean.FALSE)))))
            .setRequired(List.of(Fields.tableName));

    @Serial
    private static final long serialVersionUID = 6766760161916997489L;

    private String tableName;
    private String tableDescription;
    private Map<String, ColumnSchema> columns = new LinkedHashMap<>();
    private List<KeySchema> keys = new ArrayList<>();
    private Boolean isAudited = Boolean.FALSE;
    private Boolean isSoftDelete = Boolean.FALSE;

    public TableSchema(String tableName) {
        super();
        this.tableName = tableName;
    }

    public static TableSchema ofTable(String tableName) {
        return new TableSchema(tableName);
    }

    public TableSchema addColumn(ColumnSchema columnSchema) {
        this.columns.put(columnSchema.getName(), columnSchema);
        return this;
    }

    public TableSchema addKey(KeySchema keySchema) {
        this.keys.add(keySchema);
        return this;
    }

    public TableSchema setPrimaryKey(String columnName, List<String> columns) {
        this.keys.removeIf(k -> k.getKeyType().equals(KeyType.PRIMARY.name()));
        return this.addKey(KeySchema.ofPrimaryKey(columnName, columns));
    }

    public TableSchema addForeignKey(
            String name, List<String> columns, String referencedTable, List<String> referencedColumns) {
        return this.addKey(KeySchema.ofForeignKey(name, columns, referencedTable, referencedColumns));
    }

    public TableSchema addUniqueKey(String name, List<String> columns) {
        return this.addKey(KeySchema.uniqueKey(name, columns));
    }

    public TableSchema addIndex(String name, List<String> columns) {
        return this.addKey(KeySchema.index(name, columns));
    }
}
