package com.fincity.saas.commons.core.service.connection.appdata.mysql;

import java.util.Set;

import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.Table;
import org.jooq.impl.DSL;

/**
 * A grouped read of a storage's children, resolved into a derived table.
 *
 * One row per parent, by construction: the children are grouped by the relation
 * column before the parent ever sees them. That is what keeps paging honest, and it
 * is the difference between this and joining to the many side.
 *
 * @param alias      how the caller addresses the measures, as {@code alias.measure}
 * @param derived    the grouped SELECT, already aliased
 * @param keyColumn  the column inside it holding the parent's id
 * @param parentField the parent column it matches, {@code _id} in practice
 * @param measures   the measure names this exposes
 * @param required   INNER, so a parent with no children drops; false keeps it with
 *                   null measures
 */
public record SubQueryTable(
        String alias,
        Table<?> derived,
        String keyColumn,
        String parentField,
        Set<String> measures,
        boolean required) {

    public Condition on(String parentAlias) {
        return DSL.field(DSL.name(parentAlias, this.parentField))
                .eq(DSL.field(DSL.name(this.alias, this.keyColumn)));
    }

    public Field<Object> measure(String name) {
        return DSL.field(DSL.name(this.alias, name));
    }

    /** The prefix a measure is returned under, so two subqueries cannot collide. */
    public String outputKey(String measure) {
        return this.alias + "." + measure;
    }
}
