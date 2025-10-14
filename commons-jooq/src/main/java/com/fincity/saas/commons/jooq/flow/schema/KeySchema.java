package com.fincity.saas.commons.jooq.flow.schema;

import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.nocode.kirun.engine.json.schema.type.SchemaType;
import com.fincity.nocode.kirun.engine.json.schema.type.Type;
import com.fincity.nocode.kirun.engine.namespaces.Namespaces;
import com.fincity.saas.commons.jooq.flow.schema.enums.IndexDirection;
import com.fincity.saas.commons.jooq.flow.schema.enums.KeyType;
import com.fincity.saas.commons.jooq.flow.schema.enums.ReferenceType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;
import lombok.experimental.FieldNameConstants;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static java.util.Map.entry;

@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@FieldNameConstants
public class KeySchema extends AbstractRDBMSSchema {

    public static final Schema KEY_SCHEMA = new Schema()
            .setNamespace(Namespaces.SYSTEM)
            .setName("KeySchema")
            .setType(Type.of(SchemaType.OBJECT))
            .setProperties(Map.ofEntries(
                    entry(Fields.keyType, Schema.ofString(Fields.keyType).setEnums(KeyType.getKeyType())),
                    entry(Fields.columns, Schema.ofArray(Fields.columns, Schema.ofString(Fields.columns))),
                    entry(
                            Fields.columnDirections,
                            Schema.ofArray(
                                    Fields.columnDirections,
                                    Schema.ofString(Fields.columnDirections)
                                            .setEnums(IndexDirection.getIndexDirection()))),
                    entry(Fields.referencedTable, Schema.ofString(Fields.referencedTable)),
                    entry(
                            Fields.referencedColumns,
                            Schema.ofArray(Fields.referencedColumns, Schema.ofString("column"))),
                    entry(
                            Fields.onDelete,
                            Schema.ofString(Fields.onDelete).setEnums(ReferenceType.getReferenceTypes())),
                    entry(
                            Fields.onUpdate,
                            Schema.ofString(Fields.onUpdate).setEnums(ReferenceType.getReferenceTypes()))))
            .setRequired(List.of(Fields.keyType, Fields.columns, "name"));

    @Serial
    private static final long serialVersionUID = 5772700522998188122L;

    private String keyType;
    private List<String> columns;
    private List<String> columnDirections;
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

    public static KeySchema ofKey(String name, KeyType keyType, List<String> columns) {
        return (KeySchema)
                new KeySchema().setKeyType(keyType.name()).setColumns(columns).setName(name);
    }

    public static KeySchema ofPrimaryKey(String name, List<String> columns) {
        return ofKey(name, KeyType.PRIMARY, columns);
    }

    public static KeySchema ofForeignKey(
            String name,
            List<String> columns,
            String referencedTable,
            List<String> referencedColumns,
            ReferenceType onDelete,
            ReferenceType onUpdate) {
        return ofKey(name, KeyType.FOREIGN, columns)
                .setReferencedTable(referencedTable)
                .setReferencedColumns(referencedColumns)
                .setOnDelete(onDelete.name())
                .setOnUpdate(onUpdate.name());
    }

    public static KeySchema ofForeignKey(
            String name, List<String> columns, String referencedTable, List<String> referencedColumns) {
        return ofKey(name, KeyType.FOREIGN, columns)
                .setReferencedTable(referencedTable)
                .setReferencedColumns(referencedColumns)
                .setOnDelete(ReferenceType.NO_ACTION.name())
                .setOnUpdate(ReferenceType.NO_ACTION.name());
    }

    public static KeySchema uniqueKey(String name, List<String> columns) {
        return ofKey(name, KeyType.UNIQUE, columns);
    }

    public static KeySchema index(String name, List<String> columns) {
        return ofKey(name, KeyType.INDEX, columns);
    }

    public static KeySchema index(String name, Map<String, IndexDirection> columnsWithDirection) {
        return ofKey(name, KeyType.INDEX, new ArrayList<>(columnsWithDirection.keySet()))
                .setColumnDirections(columnsWithDirection.values().stream()
                        .map(IndexDirection::name)
                        .toList());
    }
}
