package com.fincity.saas.commons.core.service.connection.appdata.mysql;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Select;
import org.jooq.Table;
import org.jooq.impl.DSL;

import com.fincity.saas.commons.core.document.Storage;
import com.fincity.saas.commons.core.enums.StorageRelationType;
import com.fincity.saas.commons.core.model.StorageRelation;
import com.fincity.saas.commons.core.service.connection.appdata.AggregateQueryValidator;
import com.fincity.saas.commons.model.Aggregation;
import com.fincity.saas.commons.model.StorageSubQuery;
import com.fincity.saas.commons.model.condition.AggregateFunction;
import com.fincity.saas.commons.model.condition.HavingCondition;

/**
 * Builds the grouped child query that answers a question about a parent's children.
 *
 * Pure. The storage definitions are resolved by the service, because that needs the
 * override chain and therefore Spring; everything that decides what the SQL LOOKS
 * like is here so it can be asserted as text.
 */
public final class MySQLSubQueryPlanner {

    /** The measure you get when none is asked for, which is the common case. */
    public static final String DEFAULT_MEASURE = "count";

    /** The column inside the derived table holding the parent's id. */
    public static final String KEY = "__parent";

    public static final int MAX_SUBQUERIES = 3;

    private MySQLSubQueryPlanner() {
    }

    /**
     * Why this subquery list cannot be used, or null.
     *
     * {@code child} is the resolved CHILD storage for each entry, in the same order,
     * or null where it did not resolve. The relation is checked on the child rather
     * than the parent, because that is the only side that declares it.
     */
    public static String check(
            Storage parent,
            List<StorageSubQuery> subQueries,
            List<Storage> children,
            Set<String> takenAliases,
            Set<String> parentColumns) {

        if (subQueries == null || subQueries.isEmpty()) return null;

        if (subQueries.size() > MAX_SUBQUERIES)
            return "a query may carry at most " + MAX_SUBQUERIES + " subqueries";

        Set<String> aliases = new LinkedHashSet<>(takenAliases);

        for (int i = 0; i < subQueries.size(); i++) {

            StorageSubQuery sq = subQueries.get(i);

            if (sq.getStorage() == null || sq.getStorage().isBlank()) return "a subquery names no storage";
            if (sq.getRelation() == null || sq.getRelation().isBlank())
                return "subquery on '" + sq.getStorage() + "' names no relation back to " + parent.getName();

            Storage child = i < children.size() ? children.get(i) : null;
            if (child == null)
                return "subquery storage '" + sq.getStorage() + "' does not resolve for this client";

            StorageRelation relation =
                    child.getRelations() == null ? null : child.getRelations().get(sq.getRelation());

            if (relation == null)
                return "storage '" + sq.getStorage() + "' has no relation '" + sq.getRelation() + "'";

            // Named rather than inferred, and checked rather than trusted: a child
            // with two relations to the same parent is ordinary, and pointing at the
            // wrong storage entirely would read a table the caller never named.
            if (!parent.getName().equals(relation.getStorageName()))
                return "relation '" + sq.getRelation() + "' on '" + sq.getStorage() + "' points at '"
                        + relation.getStorageName() + "', not at " + parent.getName();

            if (relation.getRelationType() != StorageRelationType.TO_ONE)
                return "relation '" + sq.getRelation() + "' on '" + sq.getStorage() + "' is "
                        + relation.getRelationType()
                        + "; the child must hold a single id for its parent, which is what a to-one is";

            if (sq.getHaving() instanceof HavingCondition)
                return "a subquery's having must be a plain condition over its measure aliases";

            String alias = sq.resolvedAlias();

            if (alias == null || !AggregateQueryValidator.ALIAS_PATTERN.matcher(alias).matches())
                return "subquery alias '" + alias + "' must match "
                        + AggregateQueryValidator.ALIAS_PATTERN.pattern();

            if (!aliases.add(alias)) return "duplicate alias '" + alias + "'";

            if (parentColumns.contains(alias))
                return "subquery alias '" + alias + "' is also a column of " + parent.getName();

            String err = checkMeasures(sq, alias);
            if (err != null) return err;
        }

        return null;
    }

    private static String checkMeasures(StorageSubQuery sq, String alias) {

        if (sq.getAggregations() == null || sq.getAggregations().isEmpty()) return null;

        Set<String> measures = new LinkedHashSet<>();

        for (Aggregation a : sq.getAggregations()) {

            if (a.getFunction() == null) return "a measure of subquery '" + alias + "' has no function";

            if (a.getFunction() != AggregateFunction.COUNT && (a.getField() == null || a.getField().isBlank()))
                return a.getFunction() + " in subquery '" + alias + "' needs a field";

            if (a.getField() != null && a.getField().indexOf('$') >= 0)
                return "field '" + a.getField() + "' may not contain '$'";

            String name = a.resolvedAlias();
            if (name == null || !AggregateQueryValidator.ALIAS_PATTERN.matcher(name).matches())
                return "measure alias '" + name + "' must match "
                        + AggregateQueryValidator.ALIAS_PATTERN.pattern();

            if (!measures.add(name)) return "duplicate measure '" + name + "' in subquery '" + alias + "'";
        }

        return null;
    }

    /** The measures this subquery exposes, which is what {@code alias.x} may name. */
    public static Set<String> measures(StorageSubQuery sq) {

        Set<String> out = new LinkedHashSet<>();

        if (sq.getAggregations() == null || sq.getAggregations().isEmpty()) {
            out.add(DEFAULT_MEASURE);
            return out;
        }

        sq.getAggregations().forEach(a -> out.add(a.resolvedAlias()));
        return out;
    }

    /**
     * The grouped child query.
     *
     * Grouped by the relation column and keyed on it, so the result is one row per
     * parent whatever the children do. The HAVING stays inside: a group the caller
     * filtered out is not one of their groups, and leaving it outside would let a
     * LEFT subquery resurrect it as nulls.
     */
    public static Table<?> derived(
            DSLContext ctx,
            Table<?> childTable,
            StorageSubQuery sq,
            String relationColumn,
            Condition where,
            Condition having,
            Set<String> childJsonColumns) {

        MySQLFieldResolver resolver = MySQLFieldResolver.of(childJsonColumns);

        Field<Object> key = DSL.field(DSL.name(relationColumn));

        List<Field<?>> select = new ArrayList<>();
        select.add(key.as(KEY));

        if (sq.getAggregations() == null || sq.getAggregations().isEmpty())
            select.add(DSL.count().as(DEFAULT_MEASURE));
        else
            for (Aggregation a : sq.getAggregations())
                select.add(MySQLAggregateBuilder.measure(a, resolver).as(a.resolvedAlias()));

        var grouped = ctx.select(select)
                .from(childTable)
                // A child with no parent cannot belong to one, and would otherwise
                // form a group of its own under a null key.
                .where(where == null ? key.isNotNull() : where.and(key.isNotNull()))
                .groupBy(key);

        Select<? extends Record> complete = having == null ? grouped : grouped.having(having);

        return complete.asTable(sq.resolvedAlias());
    }

    /** Hang the derived tables off the parent. */
    public static Table<?> attach(Table<?> from, String parentAlias, List<SubQueryTable> subQueries) {

        Table<?> table = from;

        for (SubQueryTable sq : subQueries)
            table = sq.required()
                    ? table.join(sq.derived()).on(sq.on(parentAlias))
                    : table.leftJoin(sq.derived()).on(sq.on(parentAlias));

        return table;
    }

    /** Every measure, prefixed, so two subqueries of the same shape cannot collide. */
    public static List<Field<?>> selection(List<SubQueryTable> subQueries) {

        List<Field<?>> fields = new ArrayList<>();

        for (SubQueryTable sq : subQueries)
            for (String m : sq.measures()) fields.add(sq.measure(m).as(sq.outputKey(m)));

        return fields;
    }

    public static Map<String, SubQueryTable> byAlias(List<SubQueryTable> subQueries) {
        Map<String, SubQueryTable> m = new java.util.LinkedHashMap<>();
        subQueries.forEach(sq -> m.put(sq.alias(), sq));
        return m;
    }
}
