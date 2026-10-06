package com.fincity.saas.commons.core.service.connection.appdata;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.fincity.nocode.kirun.engine.json.schema.Schema;

/**
 * What a storage field may be called.
 *
 * Needed because a top-level field name becomes a MySQL COLUMN name, and a column
 * name cannot be bound as a parameter - it is concatenated into DDL between
 * backticks. A name containing a backtick closes the quoting and everything after it
 * is SQL:
 *
 * <pre>
 * field "a` INT, ADD COLUMN `pwned"
 *   -&gt; ALTER TABLE `db`.`t` ADD COLUMN `a` INT, ADD COLUMN `pwned` INT NULL
 * </pre>
 *
 * That matters more than it would in a single-tenant application. Every tenant of
 * every app shares one MySQL server, one schema each, under one connection - so DDL
 * written by whoever can edit a storage definition is not confined to their own data.
 *
 * The field name is NOT made to be an identifier, though. Schemas describe payloads
 * that arrive from outside - the HDFC collection webhook sends "IFSC Code" and
 * "Virtual Account " - and refusing those made storages that had worked on Mongo for
 * years unsaveable. So the field keeps its name and the MySQL backend stores it under
 * {@link #column}, which is the name itself when it is already an identifier (every
 * existing column) and a normalised one otherwise. The backend translates at its
 * edges, so a caller reads and writes "IFSC Code" and never sees "IFSC_Code".
 *
 * Relation keys are the exception and must be identifiers: they are authored on the
 * storage rather than received, and the join and foreign key code reads them raw.
 *
 * Nested names are not checked here. They never become columns - a nested object is
 * one JSON column - and the JSON path they are addressed through is validated
 * separately where it is built.
 */
public final class StorageFieldNames {

    /**
     * A plain identifier, within MySQL's 64-character ceiling.
     *
     * Leading digits are excluded along with everything else outside the class, which
     * also keeps the name usable unquoted in a JSON path and as a result-set alias.
     */
    private static final Pattern VALID = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]{0,62}$");

    private static final Pattern NOT_IDENTIFIER = Pattern.compile("[^A-Za-z0-9_]");

    private static final int MAX_COLUMN = 63;

    /** The row id, which every table has whatever the schema says. */
    private static final String ID = "_id";

    private StorageFieldNames() {
    }

    public static boolean valid(String name) {
        return name != null && VALID.matcher(name).matches();
    }

    /**
     * Whether a schema field may have this name at all.
     *
     * Far looser than {@link #valid}, because {@link #column} makes anything left
     * safe for DDL. What is still refused is what would break a backend other than
     * by being a column: a leading dollar is a Mongo operator, a dot is read as a
     * path by every query on both backends, and a backtick or control character is
     * never a real field and only ever an attempt.
     */
    public static boolean storable(String name) {

        if (name == null || name.isBlank() || name.charAt(0) == '$') return false;

        for (char c : name.toCharArray()) if (c < 0x20 || c == 0x7f || c == '.' || c == '`') return false;

        return true;
    }

    /**
     * The MySQL column a field is stored in.
     *
     * The identity for an identifier, so no existing column is renamed by this
     * existing. Otherwise every character outside {@code [A-Za-z0-9_]} becomes an
     * underscore, a leading digit gets one in front, and a name past MySQL's limit
     * keeps its first characters plus a hash of the whole, so two long names that
     * share a prefix do not land on one column.
     *
     * Deterministic and computed rather than stored, so every path that needs the
     * column - DDL, writes, filters, indexes - arrives at the same one without a
     * mapping to keep in step. Two fields that normalise alike are refused at save by
     * {@link #problems}.
     */
    public static String column(String name) {

        if (name == null || valid(name)) return name;

        String out = NOT_IDENTIFIER.matcher(name).replaceAll("_");
        if (out.isEmpty() || Character.isDigit(out.charAt(0))) out = "_" + out;

        if (out.length() > MAX_COLUMN)
            out = out.substring(0, MAX_COLUMN - 9) + "_" + String.format("%08x", name.hashCode());

        return out;
    }

    /**
     * A field a query is allowed to address.
     *
     * Looser than {@link #valid} on purpose, because a filter may legitimately reach
     * into a nested object by a dotted path and the segments of one are not column
     * names. What it does refuse is a segment beginning with a dollar, and that is
     * not cosmetic on the Mongo backend: a filter field is written into the query
     * document as a KEY, so a field called {@code $where} is not a field at all - it
     * is MongoDB's JavaScript operator, evaluated on the server, inside $and as
     * readily as at the top level.
     *
     * The MySQL backend does not need this; jOOQ quotes every identifier it is
     * given, so the worst a strange name does there is fail to resolve to a column.
     * Mongo has no equivalent, because the name and the operator occupy the same
     * position in the same document.
     *
     * No field in the fleet begins with a dollar, and MongoDB itself refused such
     * names outright until recently, so nothing real is excluded.
     */
    /**
     * A value a filter is allowed to compare against.
     *
     * The companion to {@link #safeQueryPath}, and needed for the same reason: on
     * Mongo a filter becomes a document, and a map reaching the value position is
     * written as one. {@code Filters.eq("price", Map.of("$gt", 0))} is not an
     * equality test against a map - it is {@code {price: {$gt: 0}}}, a different
     * comparison than the caller asked for and one the operator whitelist never saw.
     *
     * Only keys beginning with a dollar are refused, not maps as such: comparing a
     * field to an embedded document is a real thing to want, and Mongo has never
     * allowed a stored document to have a dollar-prefixed key anyway, so nothing
     * legitimate is excluded. Checked through lists and nested maps, because the
     * position of the operator is what matters, not its depth.
     */
    public static boolean safeFilterValue(Object value) {

        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (e.getKey() != null && e.getKey().toString().startsWith("$")) return false;
                if (!safeFilterValue(e.getValue())) return false;
            }
            return true;
        }

        if (value instanceof Iterable<?> list) {
            for (Object o : list) if (!safeFilterValue(o)) return false;
            return true;
        }

        return true;
    }

    public static boolean safeQueryPath(String field) {

        if (field == null || field.isBlank()) return false;

        for (String segment : field.split("\\.", -1)) if (segment.isEmpty() || segment.charAt(0) == '$') return false;

        return true;
    }

    /**
     * Every name that cannot be stored, with the reason.
     *
     * @param schema        the storage schema, top level only
     * @param relationField relation keys, which become columns without being in the schema
     */
    public static List<String> problems(Schema schema, Set<String> relationFields) {

        List<String> out = new ArrayList<>();

        Map<String, Schema> props = schema == null || schema.getProperties() == null
                ? Map.of()
                : schema.getProperties();

        // Seeded with the id so that a field normalising onto it is caught too.
        Map<String, String> byColumn = new LinkedHashMap<>();
        byColumn.put(ID, ID);

        for (String name : props.keySet()) {
            if (!storable(name)) out.add(describeField(name));
            else collision(name, byColumn, out);
        }

        if (relationFields != null)
            for (String name : relationFields) {
                if (!valid(name)) out.add(describeRelation(name));
                else collision(name, byColumn, out);
            }

        return out;
    }

    private static void collision(String name, Map<String, String> byColumn, List<String> out) {

        String column = column(name);
        String earlier = byColumn.putIfAbsent(column, name);

        if (earlier != null && !earlier.equals(name))
            out.add("field names " + quoted(earlier) + " and " + quoted(name) + " are both stored as the column "
                    + quoted(column) + "; rename one of them");
    }

    private static String describeField(String name) {
        return "field name " + quoted(name)
                + " is not usable: a field may not be blank, start with '$', or contain a dot, a backtick or"
                + " a control character";
    }

    private static String describeRelation(String name) {
        return "relation name " + quoted(name)
                + " is not usable: a relation has to start with a letter or underscore and continue with"
                + " letters, digits or underscores, up to 63 characters";
    }

    /** Printable, and never re-quoted in a way that could itself be read as code. */
    private static String quoted(String name) {

        if (name == null) return "null";

        StringBuilder sb = new StringBuilder("\"");
        for (char c : name.toCharArray()) sb.append(c < 0x20 || c == '`' || c == '"' || c == '\\' ? '?' : c);
        return sb.append('"').toString();
    }
}
