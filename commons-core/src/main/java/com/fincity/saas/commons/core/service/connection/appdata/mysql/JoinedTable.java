package com.fincity.saas.commons.core.service.connection.appdata.mysql;

import java.util.Set;

import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.Table;
import org.jooq.impl.DSL;

import com.fincity.saas.commons.model.JoinType;

/**
 * One relation resolved into something SQL can use.
 *
 * Built per tenant rather than per storage, because the target's definition is
 * overridable too: the same relation can point at a table with different columns for
 * two different clients, so the column sets below are that client's.
 *
 * @param alias        how the caller addresses this side, as {@code alias.field}
 * @param table        the target table, already qualified with the tenant database
 * @param parentField  the column on the parent holding the id
 * @param targetField  the column on the target it points at, {@code _id} in every
 *                     relation that exists today but not assumed to be
 * @param type         LEFT keeps parents that match nothing, INNER drops them
 * @param columnTypes  the target's columns and their MySQL types. The types are
 *                     carried rather than just the names because the aggregate rules
 *                     turn on them: a joined column whose type was unknown would have
 *                     to be assumed non-numeric, and SUM over it refused - which is
 *                     most of what anyone joins for
 * @param jsonColumns  of those, the ones holding JSON
 * @param dateColumns  of those, the ones the schema calls a STRING and stores as a date
 * @param fieldNames   column to field, for the target's columns whose names differ from
 *                     the field they store, so the joined object comes back keyed the
 *                     way the target declared it. Empty for nearly every storage
 */
public record JoinedTable(
        String alias,
        Table<?> table,
        String parentField,
        String targetField,
        JoinType type,
        java.util.Map<String, String> columnTypes,
        Set<String> jsonColumns,
        Set<String> dateColumns,
        java.util.Map<String, String> fieldNames) {

    public JoinedTable {
        fieldNames = fieldNames == null ? java.util.Map.of() : fieldNames;
    }

    public JoinedTable(
            String alias,
            Table<?> table,
            String parentField,
            String targetField,
            JoinType type,
            java.util.Map<String, String> columnTypes,
            Set<String> jsonColumns,
            Set<String> dateColumns) {
        this(alias, table, parentField, targetField, type, columnTypes, jsonColumns, dateColumns, null);
    }

    public Set<String> columns() {
        return this.columnTypes.keySet();
    }

    /** The equality that defines the join. */
    public Condition on(String parentAlias) {
        return DSL.field(DSL.name(parentAlias, this.parentField))
                .eq(DSL.field(DSL.name(this.alias, this.targetField)));
    }

    /** Takes a field or a column; a column is its own field name's column. */
    public Field<Object> column(String name) {
        return DSL.field(DSL.name(this.alias, MySQLColumnNames.column(name)));
    }

    /**
     * The prefix joined columns are returned under.
     *
     * Both sides have an {@code _id}, so the result of a join cannot be read into a
     * flat map without one: the second would silently overwrite the first.
     */
    public String outputKey(String column) {
        return this.alias + "." + column;
    }
}
