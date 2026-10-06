package com.fincity.saas.commons.core.service.connection.appdata.mysql;

import java.util.Map;
import java.util.Set;

import org.jooq.Field;
import org.jooq.impl.DSL;


/**
 * Turns a field name in a query into the SQL that reads it.
 *
 * Three spellings share one syntax and have to be told apart, not guessed:
 *
 * <pre>
 *   amount           a column on the parent
 *   address.city     a path into a JSON column on the parent
 *   customer.region  a column on a joined table
 *   orders.count     a measure from a subquery over the children
 * </pre>
 *
 * Resolved by precedence, and the ambiguity is removed rather than ranked: a join
 * alias is refused at validation if it collides with any column of the parent, so a
 * head can never be both an alias and a JSON column. Without that rule the same query
 * would mean different things on two clients whose storages differ, which is the
 * worst kind of difference to debug.
 */
public final class MySQLFieldResolver {

    private final String parentAlias;
    private final Set<String> jsonColumns;
    private final Map<String, JoinedTable> joins;
    private final Map<String, SubQueryTable> subQueries;
    private Set<String> textColumns = Set.of();

    private MySQLFieldResolver(
            String parentAlias,
            Set<String> jsonColumns,
            Map<String, JoinedTable> joins,
            Map<String, SubQueryTable> subQueries) {
        this.parentAlias = parentAlias;
        this.jsonColumns = jsonColumns == null ? Set.of() : jsonColumns;
        this.joins = joins == null ? Map.of() : joins;
        this.subQueries = subQueries == null ? Map.of() : subQueries;
    }

    /** No joins, and therefore no table qualifier: the SQL is what it always was. */
    public static MySQLFieldResolver of(Set<String> jsonColumns) {
        return new MySQLFieldResolver(null, jsonColumns, Map.of(), Map.of());
    }

    public static MySQLFieldResolver of(String parentAlias, Set<String> jsonColumns, Map<String, JoinedTable> joins) {
        return new MySQLFieldResolver(parentAlias, jsonColumns, joins, Map.of());
    }

    public static MySQLFieldResolver of(
            String parentAlias,
            Set<String> jsonColumns,
            Map<String, JoinedTable> joins,
            Map<String, SubQueryTable> subQueries) {
        return new MySQLFieldResolver(parentAlias, jsonColumns, joins, subQueries);
    }

    /**
     * The columns a FULLTEXT index covers, which TEXT_SEARCH matches against.
     *
     * On the resolver rather than passed alongside it because every path that
     * builds a filter already has one, and a second parameter threaded through the
     * same four methods would be the same thing with more places to forget it.
     */
    public MySQLFieldResolver withTextColumns(Set<String> textColumns) {
        this.textColumns = textColumns == null ? Set.of() : textColumns;
        return this;
    }

    public Set<String> textColumns() {
        return this.textColumns;
    }

    /** Whether this name is a JSON column on the parent, which an array always is. */
    public boolean isJsonColumn(String column) {
        return this.jsonColumns.contains(MySQLColumnNames.column(column));
    }

    public Field<Object> qualified(String column) {
        return this.parentAlias == null
                ? DSL.field(DSL.name(column))
                : DSL.field(DSL.name(this.parentAlias, column));
    }

    public boolean hasJoins() {
        return !this.joins.isEmpty() || !this.subQueries.isEmpty();
    }

    public Map<String, JoinedTable> joins() {
        return this.joins;
    }

    public Field<Object> resolve(String name) {

        int dot = name.indexOf('.');

        if (dot <= 0 || dot == name.length() - 1) return this.parentColumn(name);

        String head = name.substring(0, dot);
        String tail = name.substring(dot + 1);

        JoinedTable join = this.joins.get(head);
        if (join != null) return this.joined(join, tail);

        // A subquery measure. Checked before JSON paths for the same reason a join
        // alias is: the aliases are refused at validation if they collide with a
        // column, so a head can never be both.
        SubQueryTable sub = this.subQueries.get(head);
        if (sub != null) return sub.measure(tail);

        if (this.isJsonColumn(head)) return MySQLFilterBuilder.jsonPath(this.parentColumn(head), name, tail);

        // A field name may legitimately contain a dot, and rewriting one that matches
        // neither an alias nor a JSON column would break a filter that works today.
        return this.parentColumn(name);
    }

    private Field<Object> joined(JoinedTable join, String tail) {

        int dot = tail.indexOf('.');
        if (dot <= 0 || dot == tail.length() - 1) return join.column(tail);

        String column = tail.substring(0, dot);

        // A JSON path on the far side of a join, which is the one case where three
        // segments are meaningful rather than a mistake.
        if (join.jsonColumns().contains(MySQLColumnNames.column(column)))
            return MySQLFilterBuilder.jsonPath(join.column(column), tail, tail.substring(dot + 1));

        return join.column(tail);
    }

    /**
     * A query names fields and the table has columns, which differ for a field such
     * as "IFSC Code"; see {@link MySQLColumnNames#column}.
     */
    private Field<Object> parentColumn(String name) {
        String column = MySQLColumnNames.column(name);
        return this.parentAlias == null
                ? DSL.field(DSL.name(column))
                : DSL.field(DSL.name(this.parentAlias, column));
    }
}
