package com.fincity.saas.commons.jooq.flow.schema;

import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.saas.commons.jooq.flow.schema.enums.KeyType;
import com.fincity.saas.commons.jooq.flow.schema.enums.ReferenceType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
public class KeySchema extends Schema {

    @Serial
    private static final long serialVersionUID = 5772700522998188122L;

    private String keyType;
    private List<String> columns;
    private String name;

    private String referencedTable;
    private List<String> referencedColumns;
    private String onDelete;
    private String onUpdate;

    public KeySchema() {
        super();
    }

    public KeySchema(Schema schema) {
        super(schema);
    }

    public static KeySchema ofPrimaryKey(String name, List<String> columns) {
        return new KeySchema().setKeyType(KeyType.PRIMARY.name()).setName(name).setColumns(columns);
    }

    public static KeySchema ofForeignKey(
            String name,
            List<String> columns,
            String referencedTable,
            List<String> referencedColumns,
            ReferenceType onDelete,
            ReferenceType onUpdate) {
        return new KeySchema()
                .setKeyType(KeyType.FOREIGN.name())
                .setName(name)
                .setColumns(columns)
                .setReferencedTable(referencedTable)
                .setReferencedColumns(referencedColumns)
                .setOnDelete(onDelete.name())
                .setOnUpdate(onUpdate.name());
    }

    public static KeySchema ofForeignKey(
            String name, List<String> columns, String referencedTable, List<String> referencedColumns) {
        return new KeySchema()
                .setKeyType(KeyType.FOREIGN.name())
                .setName(name)
                .setColumns(columns)
                .setReferencedTable(referencedTable)
                .setReferencedColumns(referencedColumns)
                .setOnDelete(ReferenceType.NO_ACTION.name())
                .setOnUpdate(ReferenceType.NO_ACTION.name());
    }

    public static KeySchema uniqueKey(String name, List<String> columns) {
        return new KeySchema().setKeyType(KeyType.UNIQUE.name()).setName(name).setColumns(columns);
    }

    public static KeySchema index(String name, List<String> columns) {
        return new KeySchema().setKeyType(KeyType.INDEX.name()).setName(name).setColumns(columns);
    }
}
