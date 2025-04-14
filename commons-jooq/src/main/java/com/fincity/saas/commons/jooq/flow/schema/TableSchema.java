package com.fincity.saas.commons.jooq.flow.schema;

import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.saas.commons.jooq.flow.schema.enums.KeyType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.io.Serial;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class TableSchema extends Schema {

    @Serial
    private static final long serialVersionUID = 6766760161916997489L;

    private String tableName;
    private String tableDescription;
    private Map<String, ColumnSchema> columns;
    private List<KeySchema> keys;
    private Boolean isAudited = Boolean.FALSE;
    private Boolean isSoftDelete = Boolean.FALSE;

    public TableSchema() {
        super();
        this.columns = new HashMap<>();
        this.keys = new ArrayList<>();
    }

    public TableSchema(String tableName) {
        this();
        this.tableName = tableName;
    }

    public static TableSchema ofTable(String tableName) {
        return new TableSchema(tableName);
    }

    public TableSchema addColumn(ColumnSchema columnSchema) {
        this.columns.replace(columnSchema.getName(), columnSchema);
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
