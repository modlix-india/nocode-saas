package com.fincity.saas.commons.core.service.connection.appdata.mysql;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.impl.DSL;

import com.fincity.saas.commons.core.util.PorterStemmer;
import com.fincity.saas.commons.model.condition.AbstractCondition;
import com.fincity.saas.commons.model.condition.ComplexCondition;
import com.fincity.saas.commons.model.condition.ComplexConditionOperator;
import com.fincity.saas.commons.model.condition.FilterCondition;
import com.fincity.saas.commons.model.condition.FilterConditionOperator;

/**
 * Translates the platform's {@link AbstractCondition} vocabulary into SQL.
 *
 * The same filter has to select the same rows here as it does on the Mongo backend, so
 * this mirrors {@code MongoAppDataService.filterConditionFilter} operator for operator,
 * including how negation is applied. Where an operator cannot be expressed yet it
 * throws rather than dropping the clause, because a dropped clause means "every row".
 * The one clause it does drop is one given no value at all, and only for a read
 * ({@link #buildForRead}), because Mongo drops that one too.
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
        return build(condition, resolver, false);
    }

    /**
     * For a READ: a condition given no value drops out, as it does on Mongo.
     *
     * {@code MongoAppDataService.filterConditionFilter} answers an empty Mono for a
     * comparison whose value is null (and for IN, MATCH and MATCH_ALL with nothing to
     * compare), and the group simply goes on without it. Pages and KIRun functions lean
     * on that: an optional filter is written as {@code status = {{Page.status}}} and
     * means "any status" while nothing is picked. Here the same filter was refused with
     * a 400, so every such read had to be rewritten before an app could move to MySQL.
     *
     * Reads only. A delete keeps {@link #build(AbstractCondition, MySQLFieldResolver)},
     * which refuses: a dropped clause there deletes rows nobody named.
     */
    public static Condition buildForRead(AbstractCondition condition, MySQLFieldResolver resolver) {
        return build(condition, resolver, true);
    }

    public static Condition buildForRead(AbstractCondition condition, Set<String> jsonColumns) {
        return build(condition, MySQLFieldResolver.of(jsonColumns), true);
    }

    private static Condition build(AbstractCondition condition, MySQLFieldResolver resolver, boolean skipEmpty) {

        if (condition == null) return DSL.noCondition();

        if (condition instanceof ComplexCondition cc) return complex(cc, resolver, skipEmpty);
        if (condition instanceof FilterCondition fc)
            return skipEmpty && isEmpty(fc) ? DSL.noCondition() : filter(fc, resolver);

        throw new UnsupportedFilterException(
                condition.getClass().getSimpleName() + " is not a condition this backend supports");
    }

    /**
     * Exactly the conditions Mongo drops: no value to compare with. IS_NULL, IS_TRUE
     * and IS_FALSE never take one. A value that IS supplied but comes to nothing (an
     * IN of " , ", a BETWEEN without its toValue) is still the caller's mistake and is
     * still refused, on both backends.
     */
    static boolean isEmpty(FilterCondition fc) {

        if (fc.getOperator() == null) return false;

        boolean noMulti = fc.getMultiValue() == null || fc.getMultiValue().isEmpty();

        return switch (fc.getOperator()) {
            case IS_NULL, IS_TRUE, IS_FALSE -> false;
            case IN, MATCH_ALL -> fc.getValue() == null && noMulti;
            default -> fc.getValue() == null;
        };
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
                throw UnsupportedFilterException.malformed("'" + whole + "' is not a usable JSON path");
            expr.append('.').append(seg);
        }

        return DSL.field(
                "json_unquote(json_extract({0}, {1}))", Object.class, column, DSL.inline(expr.toString()));
    }

    /**
     * De Morgan, matching the Mongo backend exactly: a negated group negates each child
     * AND flips the operator, rather than wrapping the whole group in a NOT.
     */
    private static Condition complex(ComplexCondition cc, MySQLFieldResolver resolver, boolean skipEmpty) {

        if (cc.getConditions() == null || cc.getConditions().isEmpty()) return DSL.noCondition();

        List<Condition> parts = new ArrayList<>();
        for (AbstractCondition c : cc.getConditions()) {
            if (skipEmpty && c instanceof FilterCondition fc && isEmpty(fc)) continue;
            Condition built = build(c, resolver, skipEmpty);
            parts.add(cc.isNegate() ? DSL.not(built) : built);
        }

        if (parts.isEmpty()) return DSL.noCondition();

        boolean and = cc.getOperator() == ComplexConditionOperator.AND;
        if (cc.isNegate()) and = !and;

        return and ? DSL.and(parts) : DSL.or(parts);
    }

    private static Condition filter(FilterCondition fc, MySQLFieldResolver resolver) {

        if (fc.getOperator() == null) throw UnsupportedFilterException.malformed("a filter condition has no operator");

        switch (fc.getOperator()) {
            case TEXT_SEARCH:
                return textSearch(fc, resolver);
            case MATCH:
                return match(fc, resolver);
            case MATCH_ALL:
                return matchAll(fc, resolver);
            default:
                break;
        }

        if (fc.getField() == null || fc.getField().isBlank())
            throw UnsupportedFilterException.malformed("a filter condition has no field");

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

        String raw = fc.getValue().toString();

        if (!resolver.stemming()) {
            sql.append(") AGAINST ({").append(i).append("} IN NATURAL LANGUAGE MODE)");
            bindings.add(DSL.val(raw));
            Condition plain = DSL.condition(sql.toString(), bindings.toArray());
            return fc.isNegate() ? plain.not() : plain;
        }

        // Stemmed, which means BOOLEAN MODE, because the truncation operator is
        // the only thing that widens a stem back over the forms actually stored.
        // InnoDB does not stem, so "guides" finds nothing without this; Mongo's
        // $text has stemmed all along, and the two backends disagreed.
        List<String> prefixes = PorterStemmer.searchPrefixes(raw);

        // Every term was punctuation, a stopword, or shorter than
        // innodb_ft_min_token_size. An empty AGAINST is a syntax error, and the
        // honest answer is the one MySQL already gives for "the": no rows.
        if (prefixes.isEmpty()) return fc.isNegate() ? DSL.trueCondition() : DSL.falseCondition();

        StringBuilder against = new StringBuilder();
        for (String prefix : prefixes) {
            if (against.length() > 0) against.append(' ');
            // Bare, NOT "+prefix*". A leading + would make every term REQUIRED,
            // silently turning multi word search into AND - where both natural
            // language mode and Mongo's $text match on any term.
            against.append(prefix).append('*');
        }

        sql.append(") AGAINST ({").append(i).append("} IN BOOLEAN MODE)");
        bindings.add(DSL.val(against.toString()));

        Condition condition = DSL.condition(sql.toString(), bindings.toArray());
        return fc.isNegate() ? condition.not() : condition;
    }

    /**
     * At least one element of an array satisfies the match operator.
     *
     * Mongo's {@code $elemMatch}. An ARRAY is one JSON column here - the type mapper
     * stores it "whole as JSON" - so the array is addressable and the only question
     * was which SQL answers each operator:
     *
     * <ul>
     * <li>EQUALS is containment, which {@code JSON_CONTAINS} answers directly and can
     * use an index on a generated column later.</li>
     * <li>LIKE is {@code JSON_SEARCH}, whose whole purpose is a LIKE over the strings
     * in a document. It returns the path of the first hit, so "matched" is "not
     * null".</li>
     * <li>The comparisons have no containment form, so the array is unrolled with
     * {@code JSON_TABLE} and the comparison runs per element. An element that will
     * not convert to the compared type yields NULL and simply does not match, which
     * is the right reading of "greater than 5" over a mixed array.</li>
     * </ul>
     */
    private static Condition match(FilterCondition fc, MySQLFieldResolver resolver) {

        Field<Object> array = arrayField(fc.getField(), resolver);
        FilterConditionOperator op =
                fc.getMatchOperator() == null ? FilterConditionOperator.EQUALS : fc.getMatchOperator();

        require(fc.getValue(), "MATCH needs a value");

        Condition c =
                switch (op) {
                    case EQUALS -> jsonContains(array, jsonLiteral(fc.getValue()));
                    case LIKE -> jsonSearch(array, fc.getValue().toString());
                    case STRING_LOOSE_EQUAL -> jsonSearch(array, "%" + fc.getValue() + "%");
                    case GREATER_THAN, GREATER_THAN_EQUAL, LESS_THAN, LESS_THAN_EQUAL -> elementCompare(
                            array, op, fc.getValue());
                    default -> throw new UnsupportedFilterException(
                            "MATCH cannot use a match operator of " + op + " on this backend");
                };

        return fc.isNegate() ? c.not() : c;
    }

    /**
     * Every supplied value is present in the array. Mongo's {@code $all}.
     *
     * One {@code JSON_CONTAINS} with an ARRAY candidate, which is true only when every
     * element of the candidate is contained - not a conjunction of single-value
     * checks, which would say the same thing in more statements.
     */
    private static Condition matchAll(FilterCondition fc, MySQLFieldResolver resolver) {

        Field<Object> array = arrayField(fc.getField(), resolver);

        List<?> values = fc.getMultiValue() != null && !fc.getMultiValue().isEmpty()
                ? fc.getMultiValue()
                : fc.getValue() == null ? List.of() : List.of(fc.getValue());

        if (values.isEmpty())
            throw UnsupportedFilterException.malformed("MATCH_ALL needs either multiValue or a value");

        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) json.append(',');
            json.append(jsonLiteral(values.get(i)));
        }
        json.append(']');

        Condition c = jsonContains(array, json.toString());
        return fc.isNegate() ? c.not() : c;
    }

    /**
     * The array itself, as JSON.
     *
     * Not {@link #jsonPath}, which wraps the extract in {@code JSON_UNQUOTE} to get a
     * comparable scalar. That is right for a leaf and wrong here: unquoting an array
     * hands the JSON functions a string that merely looks like one, and
     * {@code JSON_CONTAINS} then fails on it rather than searching it.
     */
    private static Field<Object> arrayField(String name, MySQLFieldResolver resolver) {

        if (name == null || name.isBlank())
            throw UnsupportedFilterException.malformed("a filter condition has no field");

        int dot = name.indexOf('.');
        if (dot <= 0 || dot == name.length() - 1) return resolver.resolve(name);

        String head = name.substring(0, dot);
        if (!resolver.isJsonColumn(head))
            throw UnsupportedFilterException.malformed(
                    "'" + name + "' is not an array on this storage, so it cannot be matched inside");

        StringBuilder expr = new StringBuilder("$");
        for (String seg : name.substring(dot + 1).split("\\.")) {
            if (!PATH_SEGMENT.matcher(seg).matches())
                throw UnsupportedFilterException.malformed("'" + name + "' is not a usable JSON path");
            expr.append('.').append(seg);
        }

        return DSL.field(
                "json_extract({0}, {1})", Object.class, resolver.resolve(head), DSL.inline(expr.toString()));
    }

    private static Condition jsonContains(Field<Object> array, String candidateJson) {
        return DSL.condition("json_contains({0}, cast({1} as json))", array, DSL.val(candidateJson));
    }

    private static Condition jsonSearch(Field<Object> array, String pattern) {
        return DSL.condition("json_search({0}, 'one', {1}) is not null", array, DSL.val(pattern));
    }

    private static Condition elementCompare(Field<Object> array, FilterConditionOperator op, Object value) {

        // The column type decides what the comparison MEANS. A number compared as
        // text makes 9 greater than 10, which is the kind of wrong that looks right
        // in a small test and fails on real data.
        String columnType = value instanceof Number ? "decimal(38,10)" : "char(1024)";

        String comparison =
                switch (op) {
                    case GREATER_THAN -> ">";
                    case GREATER_THAN_EQUAL -> ">=";
                    case LESS_THAN -> "<";
                    default -> "<=";
                };

        // Counted rather than EXISTS, and this is not a style choice. On MySQL
        // 8.4.11 an EXISTS whose subquery is a JSON_TABLE over an outer column
        // silently matches NOTHING - no error, no warning, an empty result that
        // looks like "no rows qualify". The identical subquery as a scalar count
        // correlates correctly. Verified against the server, both forms, on the
        // same three rows.
        return DSL.condition(
                "(select count(*) from json_table({0}, '$[*]' columns (v " + columnType
                        + " path '$')) as jt where jt.v " + comparison + " {1}) > 0",
                array,
                DSL.val(value));
    }

    /**
     * A value as a JSON literal, which is what both JSON_CONTAINS candidates need.
     *
     * Written out rather than handed to a JSON library because the only shapes that
     * reach here are scalars, and a dependency for four cases is not worth the reader
     * having to go and check what it does with them.
     */
    private static String jsonLiteral(Object value) {

        if (value == null) throw UnsupportedFilterException.malformed("a match value cannot be null");

        if (value instanceof Number || value instanceof Boolean) return value.toString();

        StringBuilder out = new StringBuilder("\"");
        for (char ch : value.toString().toCharArray()) {
            switch (ch) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (ch < 0x20) out.append(String.format("\\u%04x", (int) ch));
                    else out.append(ch);
                }
            }
        }
        return out.append('"').toString();
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
                require(fc.getValue(), "BETWEEN on '" + fc.getField() + "' without a value");
                require(fc.getToValue(), "BETWEEN on '" + fc.getField() + "' without a toValue");
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
            throw UnsupportedFilterException.malformed(
                    "IN on '" + fc.getField() + "' needs either multiValue or a comma separated value");

        List<String> parts = new ArrayList<>();
        for (String p : fc.getValue().toString().split(",")) {
            String t = p.trim();
            if (!t.isEmpty()) parts.add(t);
        }

        if (parts.isEmpty())
            throw UnsupportedFilterException.malformed("IN on '" + fc.getField() + "' was given an empty list");

        return parts;
    }

    private static void require(Object value, String message) {
        if (value == null) throw UnsupportedFilterException.malformed(message);
    }
}
