package com.fincity.saas.commons.core.service.connection.appdata;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.data.domain.Sort;

import com.fincity.saas.commons.model.AggregateQuery;
import com.fincity.saas.commons.model.Aggregation;
import com.fincity.saas.commons.model.GroupByField;
import com.fincity.saas.commons.model.condition.AggregateFunction;
import com.fincity.saas.commons.model.condition.HavingCondition;

/**
 * The rules an aggregate request has to satisfy before any backend builds a query
 * from it.
 *
 * This is the security-critical step rather than a convenience. Field names become
 * pipeline paths on Mongo and column references on MySQL, and aliases become output
 * keys on both, so an unvalidated request is injection into app data. Two backends
 * doing that check separately is how one of them ends up slightly more permissive
 * than the other, and which one is anybody's guess, so the rules live here and are
 * applied by both.
 *
 * Pure, and returns the reason rather than throwing: each backend raises its own
 * message type, and a validator that threw would have to know which.
 */
public final class AggregateQueryValidator {

    /**
     * What an alias may be made of.
     *
     * Deliberately narrow. An alias is a Mongo $project key and a SQL output column
     * in the same breath, and the two disagree about what is legal; the intersection
     * is an identifier.
     */
    public static final Pattern ALIAS_PATTERN = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private AggregateQueryValidator() {
    }

    /** The reason this request is not usable, or null when it is. */
    public static String check(AggregateQuery query) {

        if (query == null) return "no aggregate query";

        if (query.getAggregations() == null || query.getAggregations().isEmpty())
            return "at least one aggregation is required";

        if (query.getHaving() instanceof HavingCondition)
            return "having must be a plain condition over the aliases; HavingCondition carries its own"
                    + " aggregate and is not supported here";

        Set<String> aliases = new LinkedHashSet<>();

        if (query.getGroupBy() != null)
            for (GroupByField g : query.getGroupBy()) {
                String err = checkGroupBy(g, aliases);
                if (err != null) return err;
            }

        for (Aggregation a : query.getAggregations()) {
            String err = checkAggregation(a, aliases);
            if (err != null) return err;
        }

        if (query.getSort() != null)
            for (Sort.Order o : query.getSort())
                if (!aliases.contains(o.getProperty()))
                    return "cannot sort on '" + o.getProperty()
                            + "'; sort is only possible on a group key or measure alias " + aliases;

        return null;
    }

    /** Every alias this query produces, which is the whole vocabulary of its result. */
    public static Set<String> aliases(AggregateQuery query) {

        Set<String> aliases = new LinkedHashSet<>();

        if (query.getGroupBy() != null) for (GroupByField g : query.getGroupBy()) aliases.add(g.resolvedAlias());
        if (query.getAggregations() != null)
            for (Aggregation a : query.getAggregations()) aliases.add(a.resolvedAlias());

        return aliases;
    }

    private static String checkGroupBy(GroupByField g, Set<String> aliases) {

        if (g.getField() == null || g.getField().isBlank()) return "a groupBy entry has no field";
        if (g.getField().indexOf('$') >= 0) return "field '" + g.getField() + "' may not contain '$'";

        String alias = g.resolvedAlias();
        if (!ALIAS_PATTERN.matcher(alias).matches())
            return "alias '" + alias + "' must match " + ALIAS_PATTERN.pattern();
        if (!aliases.add(alias)) return "duplicate alias '" + alias + "'";

        if (g.getBucket() == null) {
            if (g.getEncoding() != null)
                return "encoding is only meaningful with a bucket, on field '" + g.getField() + "'";
            return null;
        }

        try {
            ZoneId.of(g.resolvedTimezone());
        } catch (DateTimeException e) {
            return "'" + g.getTimezone() + "' is not a known IANA timezone";
        }

        return null;
    }

    private static String checkAggregation(Aggregation a, Set<String> aliases) {

        if (a.getFunction() == null) return "an aggregation has no function";

        if (a.getFunction() != AggregateFunction.COUNT && (a.getField() == null || a.getField().isBlank()))
            return a.getFunction() + " needs a field";

        if (a.getField() != null && a.getField().indexOf('$') >= 0)
            return "field '" + a.getField() + "' may not contain '$'";

        String alias = a.resolvedAlias();
        if (alias == null || !ALIAS_PATTERN.matcher(alias).matches())
            return "alias '" + alias + "' must match " + ALIAS_PATTERN.pattern();
        if (!aliases.add(alias)) return "duplicate alias '" + alias + "'";

        return null;
    }
}
