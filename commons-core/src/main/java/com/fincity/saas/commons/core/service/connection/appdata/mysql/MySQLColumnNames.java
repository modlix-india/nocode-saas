package com.fincity.saas.commons.core.service.connection.appdata.mysql;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.saas.commons.core.model.StorageColumnDefinition;
import com.fincity.saas.commons.core.service.connection.appdata.StorageFieldNames;

/**
 * The edge between a storage's field names and the columns they are stored in.
 *
 * A field called "IFSC Code" is stored in the column {@code IFSC_Code}; see
 * {@link StorageFieldNames#column}. Everything inside the MySQL backend - DDL,
 * indexes, drift, migration - works in column names, because it is handed a
 * {@link #physical} schema whose properties are keyed by column. What comes in from a
 * caller is translated with {@link #toColumns} and what goes back with
 * {@link #toFields}, so a caller only ever sees the names it declared.
 *
 * For every storage whose fields are already identifiers, which is all of them that
 * existed when this was written, every method here is the identity.
 */
public final class MySQLColumnNames {

    private MySQLColumnNames() {
    }

    /**
     * The resolved schema as MySQL sees it.
     *
     * A subclass rather than a pair, so that it can travel through every path that
     * already takes a Schema - there are a dozen - and still be asked, at the point a
     * row is handed back, which field each column belongs to. Returned unchanged when
     * no field needed renaming, so the ordinary case allocates nothing.
     */
    public static Schema physical(Schema resolved) {

        if (resolved == null || resolved instanceof Physical || resolved.getProperties() == null) return resolved;

        Map<String, String> fieldByColumn = new LinkedHashMap<>();
        for (String field : resolved.getProperties().keySet()) {
            String column = StorageFieldNames.column(field);
            if (!column.equals(field)) fieldByColumn.put(column, field);
        }

        if (fieldByColumn.isEmpty()) return resolved;

        Map<String, Schema> props = new LinkedHashMap<>();
        resolved.getProperties().forEach((k, v) -> props.put(StorageFieldNames.column(k), v));

        List<String> required = null;
        if (resolved.getRequired() != null) {
            required = new ArrayList<>();
            for (String r : resolved.getRequired()) required.add(StorageFieldNames.column(r));
        }

        Physical out = new Physical(resolved, fieldByColumn);
        out.setProperties(props);
        out.setRequired(required);
        return out;
    }

    /**
     * The column a name in a query reads.
     *
     * A dotted name is left exactly as given. No field can contain a dot (it is
     * refused at save), so one that reaches here matched no alias and no JSON column,
     * and it must fail as the unknown column it is rather than be normalised onto
     * {@code some_thing} and quietly read a different one.
     */
    public static String column(String name) {
        return name == null || name.indexOf('.') >= 0 ? name : StorageFieldNames.column(name);
    }

    /** Column to field, for the columns whose names differ. Empty when none do. */
    public static Map<String, String> fieldNames(Schema physical) {
        return physical instanceof Physical p ? p.fieldByColumn : Map.of();
    }

    /** A caller's row, keyed the way the table is. */
    public static Map<String, Object> toColumns(Map<String, Object> data) {

        if (data == null) return null;

        Map<String, Object> out = new LinkedHashMap<>();
        data.forEach((k, v) -> out.put(StorageFieldNames.column(k), v));
        return out;
    }

    /** A row from the table, keyed the way the caller declared it. */
    public static Map<String, Object> toFields(Map<String, Object> row, Schema physical) {
        return toFields(row, fieldNames(physical));
    }

    public static Map<String, Object> toFields(Map<String, Object> row, Map<String, String> fieldByColumn) {

        if (row == null || fieldByColumn == null || fieldByColumn.isEmpty()) return row;

        Map<String, Object> out = new LinkedHashMap<>();
        row.forEach((k, v) -> out.put(fieldByColumn.getOrDefault(k, k), v));
        return out;
    }

    /** Column definitions are written against field names, and read against columns. */
    public static Map<String, StorageColumnDefinition> columnDefinitions(Map<String, StorageColumnDefinition> defs) {

        if (defs == null || defs.isEmpty()) return defs;

        boolean renamed = false;
        for (String k : defs.keySet()) renamed |= !k.equals(StorageFieldNames.column(k));
        if (!renamed) return defs;

        Map<String, StorageColumnDefinition> out = new LinkedHashMap<>();
        defs.forEach((k, v) -> out.put(StorageFieldNames.column(k), v));
        return out;
    }

    private static final class Physical extends Schema {

        private static final long serialVersionUID = 1L;

        private final transient Map<String, String> fieldByColumn;

        private Physical(Schema resolved, Map<String, String> fieldByColumn) {
            super(resolved);
            this.fieldByColumn = Map.copyOf(fieldByColumn);
        }
    }
}
