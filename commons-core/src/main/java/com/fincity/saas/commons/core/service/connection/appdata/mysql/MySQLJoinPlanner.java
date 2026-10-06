package com.fincity.saas.commons.core.service.connection.appdata.mysql;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jooq.Field;
import org.jooq.Table;
import org.jooq.impl.DSL;

import com.fincity.saas.commons.core.document.Storage;
import com.fincity.saas.commons.core.enums.StorageRelationType;
import com.fincity.saas.commons.core.model.StorageRelation;
import com.fincity.saas.commons.core.service.connection.appdata.AggregateQueryValidator;
import com.fincity.saas.commons.model.JoinType;
import com.fincity.saas.commons.model.StorageJoin;

/**
 * Validates a join list and assembles the SQL around it.
 *
 * Pure. Resolving each target storage for the tenant needs Spring and happens in the
 * service; everything that decides what the query LOOKS like is here, so it can be
 * asserted as text.
 */
public final class MySQLJoinPlanner {

    /** The parent's table alias once there is anything to disambiguate it from. */
    public static final String PARENT = "p";

    /**
     * A ceiling on joins in one query.
     *
     * Not a performance tuning knob. Each join multiplies the planner's search space,
     * and a stored query that fans out across six tables is far more likely to be a
     * mistake than a requirement - and it arrives from a definition, so nobody is
     * watching when it runs.
     */
    public static final int MAX_JOINS = 5;

    private MySQLJoinPlanner() {
    }

    /**
     * Why this join list cannot be used, or null.
     *
     * The relation and alias checks are the security-relevant ones: both become
     * identifiers in the generated SQL, and the relation also chooses which table is
     * read. A caller that could name an arbitrary relation could read an arbitrary
     * storage of the same tenant, regardless of its own readAuth.
     */
    public static String check(Storage storage, List<StorageJoin> joins, Set<String> parentColumns) {
        return check(storage, joins, parentColumns, Set.of());
    }

    /**
     * @param parentJsonColumns the parent's JSON columns, which an alias may never
     *                          shadow: {@code alias.x} would then be readable as both
     *                          a joined column and a path, and which one it meant
     *                          would depend on the client whose storage was loaded
     */
    public static String check(
            Storage storage, List<StorageJoin> joins, Set<String> parentColumns, Set<String> parentJsonColumns) {

        if (joins == null || joins.isEmpty()) return null;

        if (joins.size() > MAX_JOINS) return "a query may join at most " + MAX_JOINS + " relations";

        Map<String, StorageRelation> relations =
                storage.getRelations() == null ? Map.of() : storage.getRelations();

        Set<String> aliases = new LinkedHashSet<>();

        for (StorageJoin join : joins) {

            if (join.getRelation() == null || join.getRelation().isBlank()) return "a join names no relation";

            StorageRelation relation = relations.get(join.getRelation());
            if (relation == null)
                return "storage " + storage.getName() + " has no relation '" + join.getRelation() + "'";

            if (relation.getRelationType() != StorageRelationType.TO_ONE)
                return "relation '" + join.getRelation() + "' is " + relation.getRelationType()
                        + ". A to-many relation stores a list of ids in one column, so joining through it"
                        + " cannot use an index and would scan the parent for every row. Read it eagerly"
                        + " instead, or model it as its own storage.";

            String alias = join.resolvedAlias();

            if (alias == null || !AggregateQueryValidator.ALIAS_PATTERN.matcher(alias).matches())
                return "join alias '" + alias + "' must match " + AggregateQueryValidator.ALIAS_PATTERN.pattern();

            if (!aliases.add(alias)) return "duplicate join alias '" + alias + "'";

            // A relation is KEYED by the parent column that holds the id, so the
            // default alias is always also a column - and shadowing that column is
            // exactly right. The eager fetch already replaces the id with the related
            // object under the same name, and `customer.region` cannot mean anything
            // else when `customer` holds a single id.
            // Checked first because it is the more specific reason, and both
            // refusals would otherwise fire on the same alias.
            if (parentJsonColumns.contains(alias))
                return "join alias '" + alias + "' is a JSON column of " + storage.getName()
                        + "; a path into it would be spelled the same way";

            if (!alias.equals(join.getRelation()) && parentColumns.contains(alias))
                return "join alias '" + alias + "' is also a column of " + storage.getName()
                        + "; give the join a different alias";
        }

        return null;
    }

    /** The FROM clause: the parent aliased, with each join hung off it. */
    public static Table<?> from(Table<?> parent, List<JoinedTable> joins) {

        Table<?> table = parent.as(PARENT);

        for (JoinedTable join : joins) {
            Table<?> target = join.table().as(join.alias());
            table = join.type() == JoinType.INNER
                    ? table.join(target).on(join.on(PARENT))
                    : table.leftJoin(target).on(join.on(PARENT));
        }

        return table;
    }

    /**
     * Every column the caller gets back, with the joined ones prefixed.
     *
     * Named explicitly rather than {@code p.*, a.*}: both sides have an {@code _id}
     * and a flat result map would keep whichever came last, so one row's identity
     * would silently become another's.
     */
    public static List<Field<?>> selection(Set<String> parentColumns, List<JoinedTable> joins) {

        List<Field<?>> fields = new ArrayList<>();

        for (String c : parentColumns) fields.add(DSL.field(DSL.name(PARENT, c)));

        for (JoinedTable join : joins)
            for (String c : join.columns()) fields.add(join.column(c).as(join.outputKey(c)));

        return fields;
    }

    /**
     * Fold the prefixed columns back into an object per join.
     *
     * The eager fetch already returns a related row as a nested object under the
     * relation's name, and a consumer should not be able to tell which path served
     * it. A LEFT join that matched nothing contributes nulls, and that whole side is
     * dropped rather than returned as an object full of them - which is also what
     * eager does.
     */
    public static Map<String, Object> nest(Map<String, Object> flat, List<JoinedTable> joins) {
        return nest(flat, joins, List.of());
    }

    public static Map<String, Object> nest(
            Map<String, Object> flat, List<JoinedTable> joins, List<SubQueryTable> subQueries) {

        if (joins.isEmpty() && subQueries.isEmpty()) return flat;

        Map<String, Object> out = new LinkedHashMap<>();
        Map<String, Map<String, Object>> nested = new LinkedHashMap<>();

        flat.forEach((k, v) -> {
            int dot = k.indexOf('.');
            if (dot <= 0) {
                out.put(k, v);
                return;
            }
            nested.computeIfAbsent(k.substring(0, dot), a -> new LinkedHashMap<>()).put(k.substring(dot + 1), v);
        });

        for (JoinedTable join : joins) {
            Map<String, Object> side = nested.get(join.alias());
            if (side == null) continue;

            boolean matched = side.values().stream().anyMatch(java.util.Objects::nonNull);

            // The object replaces the id column it was reached through, which is what
            // the eager fetch does. When nothing matched, the key is removed rather
            // than left holding the raw id - also what eager does, and the reason is
            // that a dangling id looks like data while an absent key looks like what
            // it is.
            if (matched) out.put(join.alias(), MySQLColumnNames.toFields(side, join.fieldNames()));
            else out.remove(join.alias());
        }

        for (SubQueryTable sq : subQueries) {
            Map<String, Object> side = nested.get(sq.alias());
            if (side == null) continue;

            // Unlike a join, an unmatched subquery keeps its object. A LEFT subquery
            // exists precisely to say "this parent has none", and a null count is
            // that answer; dropping the key would make "none" and "not asked"
            // indistinguishable.
            out.put(sq.alias(), side);
        }

        return out;
    }
}
