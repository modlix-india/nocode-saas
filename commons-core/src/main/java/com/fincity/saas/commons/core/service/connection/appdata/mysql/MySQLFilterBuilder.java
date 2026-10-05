package com.fincity.saas.commons.core.service.connection.appdata.mysql;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.impl.DSL;

import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.model.condition.ComplexCondition;
import com.fincity.saas.commons.model.condition.ComplexConditionOperator;
import com.fincity.saas.commons.model.condition.FilterCondition;

/**
 * Translates the platform's {@link AbstractCondition} vocabulary into SQL.
 *
 * The same filter has to select the same rows here as it does on the Mongo backend, so
 * this mirrors {@code MongoAppDataService.filterConditionFilter} operator for operator,
 * including how negation is applied. Where an operator cannot be expressed yet it
 * throws rather than dropping the clause, because a dropped clause means "every row".
 *
 * Pure: JOOQ renders SQL without a connection, so every operator and every negation can
 * be asserted as text in a unit test.
 */
public final class MySQLFilterBuilder {

    /**
     * What may appear in a JSON path segment.
     *
     * The path is inlined into the SQL rather than bound, because MySQL takes it as a
     * string literal inside JSON_EXTRACT and a bind there is not the same thing. So it
     * is checked rather than trusted: a field name arrives from a stored filter and a
     * stored filter is only as trustworthy as whoever saved it.
     */
    private static final Pattern PATH_SEGMENT = Pattern.compile("^[A-Za-z0-9_$\\[\\]]+$");

    private MySQLFilterBuilder() {
    }

    public static Condition build(AbstractCondition condition) {
        return build(condition, Set.of());
    }

    /**
     * @param jsonColumns the storage's JSON columns, so a dotted field can be read as
     *                    a path inside one. Without them a filter on {@code
     *                    address.city} becomes a column of that literal name and the
     *                    query fails on a table that is perfectly correct.
     */
    public static Condition build(AbstractCondition condition, Set<String> jsonColumns) {
        return build(condition, MySQLFieldResolver.of(jsonColumns));
    }

    /**
     * @param resolver decides what each field name refers to: a column, a path into a
     *                 JSON column, or a column on a joined table
     */
    public static Condition build(AbstractCondition condition, MySQLFieldResolver resolver) {

        if (condition == null) return DSL.noCondition();

        if (condition instanceof ComplexCondition cc) return complex(cc, resolver);
        if (condition instanceof FilterCondition fc) return filter(fc, resolver);

        throw new UnsupportedFilterException(
                condition.getClass().getSimpleName() + " is not a condition this backend supports");
    }

    /**
     * A column, or a path into one.
     *
     * {@code address.city} on a JSON column {@code address} reads the path; the same
     * name with no matching JSON column stays a column name, because a field may
     * legitimately contain a dot and guessing the other way would break it.
     *
     * JSON_UNQUOTE because an extracted string arrives with its quotes: six characters
     * for {@code Pune}, not four. Equality survives that, because MySQL coerces a JSON
     * scalar when comparing it to a string, which is precisely what makes the unquote
     * easy to leave out - the obvious test passes. LIKE is where it bites: an anchored
     * pattern matches against the leading quote and returns nothing at all.
     *
     * What comes back is text either way, so a numeric comparison on a path relies on
     * MySQL coercing it back rather than on a typed column. That works, and it is also
     * exactly the precision the note on a nested column is warning about.
     */
    static Field<Object> fieldOf(String name, Set<String> jsonColumns) {
        return MySQLFieldResolver.of(jsonColumns).resolve(name);
    }

    /**
     * A path into a JSON column.
     *
     * The path is inlined into the SQL because MySQL takes it as a string literal
     * inside JSON_EXTRACT and a bind there is not the same thing, so it is validated
     * rather than trusted.
     */
    static Field<Object> jsonPath(Field<Object> column, String whole, String path) {

        StringBuilder expr = new StringBuilder("$");
        for (String seg : path.split("\\.")) {
            if (!PATH_SEGMENT.matcher(seg).matches())
                throw new UnsupportedFilterException("'" + whole + "' is not a usable JSON path");
            expr.append('.').append(seg);
        }

        return DSL.field(
                "json_unquote(json_extract({0}, {1}))", Object.class, column, DSL.inline(expr.toString()));
    }

    /**
     * De Morgan, matching the Mongo backend exactly: a negated group negates each child
     * AND flips the operator, rather than wrapping the whole group in a NOT.
     */
    private static Condition complex(ComplexCondition cc, MySQLFieldResolver resolver) {

        if (cc.getConditions() == null || cc.getConditions().isEmpty()) return DSL.noCondition();

        List<Condition> parts = new ArrayList<>();
        for (AbstractCondition c : cc.getConditions()) {
            Condition built = build(c, resolver);
            parts.add(cc.isNegate() ? DSL.not(built) : built);
        }

        boolean and = cc.getOperator() == ComplexConditionOperator.AND;
        if (cc.isNegate()) and = !and;

        return and ? DSL.and(parts) : DSL.or(parts);
    }

    private static Condition filter(FilterCondition fc, MySQLFieldResolver resolver) {

        if (fc.getOperator() == null) throw new UnsupportedFilterException("a filter condition has no operator");

        switch (fc.getOperator()) {
            case TEXT_SEARCH:
                return textSearch(fc, resolver);
            case MATCH:
            case MATCH_ALL:
                throw new UnsupportedFilterException(fc.getOperator()
                        + " matches inside an array, which needs JSON_CONTAINS and a decision about how array"
                        + " fields are stored");
            default:
                break;
        }

        if (fc.getField() == null || fc.getField().isBlank())
            throw new UnsupportedFilterException("a filter condition has no field");

        Field<Object> field = resolver.resolve(fc.getField());
        Condition c = positive(fc, field, resolver);

        return fc.isNegate() ? DSL.not(c) : c;
    }

    /**
     * Full-text search over the columns the storage declared as text-indexed.
     *
     * The same fields Mongo puts in its one text index, which is what makes the
     * same query mean the same thing on both backends. Natural language mode
     * because that is what Mongo's {@code $text} does: words, not a boolean
     * expression the caller has to learn.
     *
     * Still refused when the storage declares no text fields, and the message says
     * so - that is a definition that never asked for the index, not a backend that
     * cannot do it.
     */
    private static Condition textSearch(FilterCondition fc, MySQLFieldResolver resolver) {

        Set<String> columns = resolver.textColumns();

        if (columns.isEmpty())
            throw new UnsupportedFilterException(
                    "TEXT_SEARCH needs the storage to declare textIndexFields, and this one declares none");

        if (fc.getValue() == null || fc.getValue().toString().isBlank())
            throw new UnsupportedFilterException("TEXT_SEARCH was given nothing to search for");

        List<Object> bindings = new java.util.ArrayList<>();
        StringBuilder sql = new StringBuilder("MATCH(");

        int i = 0;
        for (String column : columns) {
            if (i > 0) sql.append(", ");
            sql.append('{').append(i++).append('}');
            bindings.add(resolver.qualified(column));
        }

        sql.append(") AGAINST ({").append(i).append("} IN NATURAL LANGUAGE MODE)");
        bindings.add(DSL.val(fc.getValue().toString()));

        Condition condition = DSL.condition(sql.toString(), bindings.toArray());
        return fc.isNegate() ? condition.not() : condition;
    }

    private static Condition positive(FilterCondition fc, Field<Object> field, MySQLFieldResolver resolver) {

        switch (fc.getOperator()) {
            case IS_TRUE:
                return field.isTrue();
            case IS_FALSE:
                return field.isFalse();
            case IS_NULL:
                return field.isNull();
            case IN:
                return field.in(multiValue(fc));
            case BETWEEN:
                require(fc.getValue(), "BETWEEN needs a value");
                require(fc.getToValue(), "BETWEEN needs a toValue");
                return field.between(fc.getValue()).and(fc.getToValue());
            case LIKE:
                require(fc.getValue(), "LIKE needs a value");
                return field.like(fc.getValue().toString());
            case STRING_LOOSE_EQUAL:
                require(fc.getValue(), "STRING_LOOSE_EQUAL needs a value");
                return field.like("%" + fc.getValue() + "%");
            default:
                break;
        }

        require(fc.getValue(), fc.getOperator() + " needs a value");

        // A value field compares two columns rather than a column and a literal.
        Object right = fc.isValueField() ? resolver.resolve(fc.getValue().toString()) : fc.getValue();

        return switch (fc.getOperator()) {
            case EQUALS -> field.eq(right);
            case GREATER_THAN -> field.gt(right);
            case GREATER_THAN_EQUAL -> field.ge(right);
            case LESS_THAN -> field.lt(right);
            case LESS_THAN_EQUAL -> field.le(right);
            default -> throw new UnsupportedFilterException(fc.getOperator() + " is not supported on this backend");
        };
    }

    /**
     * IN accepts an explicit multiValue, or a comma separated single value, which is
     * what the rest of the platform allows and therefore what a stored filter may hold.
     */
    private static List<?> multiValue(FilterCondition fc) {

        if (fc.getMultiValue() != null && !fc.getMultiValue().isEmpty()) return fc.getMultiValue();

        if (fc.getValue() == null)
            throw new UnsupportedFilterException("IN needs either multiValue or a comma separated value");

        List<String> parts = new ArrayList<>();
        for (String p : fc.getValue().toString().split(",")) {
            String t = p.trim();
            if (!t.isEmpty()) parts.add(t);
        }

        if (parts.isEmpty()) throw new UnsupportedFilterException("IN was given an empty list");

        return parts;
    }

    private static void require(Object value, String message) {
        if (value == null) throw new UnsupportedFilterException(message);
    }
}
