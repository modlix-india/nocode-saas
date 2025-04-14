package com.fincity.saas.commons.jooq.flow.schema;

import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.nocode.kirun.engine.json.schema.string.StringFormat;
import com.fincity.nocode.kirun.engine.json.schema.type.SchemaType;
import com.fincity.nocode.kirun.engine.json.schema.type.Type;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serial;

@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
public class ColumnSchema extends Schema {

    @Serial
    private static final long serialVersionUID = 4252507002565625625L;

    private Boolean primaryKey = Boolean.FALSE;
    private Boolean autoIncrement = Boolean.FALSE;
    private Boolean unique = Boolean.FALSE;
    private Boolean indexed = Boolean.FALSE;
    private KeySchema foreignKey;
    private Integer precision;
    private Integer scale;
    private String columnDefinition;

    public ColumnSchema() {
        super();
    }

    public ColumnSchema(Schema schema) {
        super(schema);
    }

    public static ColumnSchema ofColumn(String columnName, SchemaType schemaType) {
        return (ColumnSchema) new ColumnSchema().setName(columnName).setType(Type.of(schemaType));
    }

    public static ColumnSchema ofVarchar(String columnName, int length) {
        return (ColumnSchema) ofColumn(columnName, SchemaType.STRING).setMaxLength(length);
    }

    public static ColumnSchema ofDateTime(String columnName) {
        return (ColumnSchema) ofColumn(columnName, SchemaType.STRING).setFormat(StringFormat.DATETIME);
    }

    public static ColumnSchema ofDecimal(String columnName, int precision, int scale) {
        return ofColumn(columnName, SchemaType.DOUBLE).setPrecision(precision).setScale(scale);
    }
}
