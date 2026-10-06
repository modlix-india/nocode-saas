package com.fincity.saas.commons.core.service.connection.appdata.mysql;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import com.fincity.nocode.kirun.engine.json.schema.Schema;
import com.fincity.nocode.kirun.engine.json.schema.string.StringFormat;
import com.fincity.nocode.kirun.engine.json.schema.type.SchemaType;
import com.fincity.saas.commons.core.model.StorageColumnDefinition;

/**
 * Turns a storage's KIRun schema into MySQL columns.
 *
 * Pure: no connection, no storage lookup, no I/O. Every DDL decision this backend
 * makes starts here, so it is worth being able to test the whole mapping without a
 * database.
 */
public final class MySQLTypeMapper {

    /** Row id column. ULID is 26 Crockford base32 characters, fixed width. */
    public static final String ID_COLUMN = "_id";

    public static final String ID_TYPE = "CHAR(26)";

    /** Anything whose shape is deeper than one column can express. */
    public static final String JSON_TYPE = "JSON";

    /**
     * What a field declared {@code STRING + DECIMAL} becomes.
     *
     * A default rather than a decision: nothing in the schema carries a scale yet
     * ({@code multipleOf} is a Long, so it cannot express 0.01), and 19,4 is the
     * conventional shape for money. It matters that this is SOMETHING rather than
     * VARCHAR, because the aggregate rules refuse to SUM a non-numeric column - so
     * leaving it as text would make the exact values unsummable, which is most of
     * what anyone stores a decimal for.
     */
    public static final String DEFAULT_DECIMAL = "DECIMAL(19,4)";

    /**
     * MySQL's row size limit means a VARCHAR beyond this is a liability, and a column
     * this wide is not a column anyone joins on anyway.
     */
    private static final int MAX_VARCHAR = 4000;

    private static final int DEFAULT_VARCHAR = 255;

    private static final int EMAIL_LENGTH = 320;

    private MySQLTypeMapper() {
    }

    /**
     * Every column for a storage, ordered by field name so two runs over the same
     * schema produce the same DDL and a diff between versions is readable.
     *
     * The id column is NOT included: it is fixed, always present, and always the
     * primary key, so it is the table builder's business rather than the schema's.
     */
    public static List<MySQLColumn> columns(Schema storageSchema) {
        return columns(storageSchema, null);
    }

    /**
     * Every column for a storage, with the author's physical overrides applied.
     *
     * An override replaces the guess for that one field and nothing else. It is not a
     * second schema: the KIRun schema still validates every write on both backends,
     * and a field with no definition is mapped exactly as it always was.
     */
    public static List<MySQLColumn> columns(
            Schema storageSchema, Map<String, StorageColumnDefinition> definitions) {

        List<MySQLColumn> out = new ArrayList<>();
        if (storageSchema == null) return out;

        Map<String, Schema> props = storageSchema.getProperties();
        if (props == null || props.isEmpty()) return out;

        Set<String> required =
                storageSchema.getRequired() == null ? Set.of() : Set.copyOf(storageSchema.getRequired());

        // TreeMap for a stable, name-ordered result regardless of the source map's type.
        for (Map.Entry<String, Schema> e : new TreeMap<>(props).entrySet()) {
            if (ID_COLUMN.equals(e.getKey())) continue;
            out.add(column(e.getKey(), e.getValue(), !required.contains(e.getKey()), declared(definitions, e.getKey())));
        }

        return out;
    }

    private static StorageColumnDefinition.MySQL declared(
            Map<String, StorageColumnDefinition> definitions, String field) {

        if (definitions == null) return null;
        StorageColumnDefinition def = definitions.get(field);
        return def == null || def.getMysql() == null || def.getMysql().getType() == null ? null : def.getMysql();
    }

    public static MySQLColumn column(String name, Schema fieldSchema, boolean nullable) {
        return column(name, fieldSchema, nullable, null);
    }

    /**
     * The column for one field, with the declared physical shape taking precedence.
     *
     * The override wins outright rather than being merged with the guess. Merging
     * would mean an author who asked for DECIMAL(10,2) could still get a note about
     * the scale not being configurable, or worse, a nullable flag from one source and
     * a type from the other. Nullability is the one thing that still comes from the
     * schema, because {@code required} is the schema saying it, not a storage detail.
     */
    public static MySQLColumn column(
            String name, Schema fieldSchema, boolean nullable, StorageColumnDefinition.MySQL declared) {

        if (declared != null && declared.getType() != null)
            return new MySQLColumn(
                    name,
                    MySQLColumnDefinitions.type(declared),
                    nullable,
                    null,
                    MySQLColumnDefinitions.collation(declared));

        if (fieldSchema == null) return json(name, nullable, "no schema for this field");

        // A ref still present here is one SchemaRefResolver could not follow: too deep,
        // cyclic, or pointing at something that is gone. A recursive type is a real
        // thing to write and JSON is the honest column for it, so this is a note rather
        // than a failure.
        if (fieldSchema.getRef() != null && !fieldSchema.getRef().isBlank())
            return json(name, nullable, "unresolved reference " + fieldSchema.getRef()
                    + ", so the shape is not known at publish time");

        Set<SchemaType> types = allowedTypes(fieldSchema);

        if (types.isEmpty()) return untyped(name, fieldSchema, nullable);

        // A field that admits more than one real type cannot be a typed column. NULL
        // alongside one other type is the exception: that is just nullability spelled
        // in the type, not a genuine union.
        Set<SchemaType> real = new java.util.LinkedHashSet<>(types);
        real.remove(SchemaType.NULL);

        if (real.size() > 1)
            return json(name, nullable, "declares several types " + real + ", so it cannot be a typed column");

        if (real.isEmpty()) return json(name, nullable, "declares only NULL");

        return switch (real.iterator().next()) {
            case STRING -> stringColumn(name, fieldSchema, nullable);
            case INTEGER -> new MySQLColumn(name, "INT", nullable);
            case LONG -> new MySQLColumn(name, "BIGINT", nullable);
            case FLOAT -> new MySQLColumn(name, "FLOAT", nullable);
            case DOUBLE -> new MySQLColumn(name, "DOUBLE", nullable);
            case BOOLEAN -> new MySQLColumn(name, "TINYINT(1)", nullable);
            case OBJECT -> json(name, nullable, nested(fieldSchema));
            case ARRAY -> json(name, nullable, "an array, stored whole as JSON");
            case NULL -> json(name, nullable, "declares only NULL");
        };
    }

    /**
     * Nesting stops at the column boundary, and that is a decision rather than a
     * limitation.
     *
     * A nested object could be flattened into {@code address_city},
     * {@code address_line1} and so on, which reads better in a table viewer. It is the
     * wrong trade here: the nested shape is itself overridable per client, so
     * flattening turns one edit to a shared sub-schema into an ALTER on every tenant of
     * every storage that embeds it, and a field appearing or disappearing inside a
     * nested object becomes a destructive column change rather than a value change.
     * One JSON column absorbs all of that without any DDL at all.
     *
     * What it costs is joinability: the whole reason for being on MySQL is joins, and
     * you cannot usefully join on a JSON path. So anything meant to be joined on
     * belongs at the top level of the storage, and the note is there to say so while
     * the author can still move it.
     */
    private static String nested(Schema fieldSchema) {

        Map<String, Schema> props = fieldSchema.getProperties();
        int count = props == null ? 0 : props.size();

        return count == 0
                ? "an object with no declared properties, stored whole as JSON"
                : "a nested object (" + count + " field(s)), stored whole as JSON: readable and filterable by path,"
                        + " but not joinable or indexable. Lift a field to the top level to join on it.";
    }

    /**
     * No declared type, but the shape often says what it is anyway.
     *
     * Declaring properties and omitting {@code type} is a common way to write an object
     * and treating it as untyped would make it a JSON column for the wrong reason, with
     * a note that tells the author nothing useful about what to fix.
     */
    private static MySQLColumn untyped(String name, Schema fieldSchema, boolean nullable) {

        if (fieldSchema.getProperties() != null && !fieldSchema.getProperties().isEmpty())
            return json(name, nullable, "no declared type but it has properties, so it is treated as "
                    + nested(fieldSchema));

        if (fieldSchema.getItems() != null)
            return json(name, nullable, "no declared type but it has items, so it is treated as an array");

        return json(name, nullable, "no declared type, stored as JSON. Declare a type to get a real column.");
    }

    private static MySQLColumn json(String name, boolean nullable, String note) {
        return new MySQLColumn(name, JSON_TYPE, nullable, note);
    }

    /**
     * Whether a value bound to this column has to be JSON text.
     *
     * The write path needs this because a Map or a List reaching the driver as itself
     * is not something the driver can bind, and the read path needs it because what
     * comes back is a string that the caller is expecting to be an object again.
     */
    public static boolean isJson(String columnType) {
        return JSON_TYPE.equalsIgnoreCase(columnType);
    }

    /**
     * Columns the schema calls a STRING but that are stored as a real date.
     *
     * The column type is the whole reason to be on a relational backend; the schema
     * type is what the validator checks a write against. Both are true at once, and
     * the read path has to turn one back into the other.
     */
    public static Set<String> dateStringColumns(Schema storageSchema) {
        return dateStringColumns(storageSchema, null);
    }

    /**
     * Derived from the columns that were actually planned, not from the schema format.
     *
     * It has to be. Once a field can declare its own column type, the schema and the
     * column can disagree in both directions: a STRING with no format at all becomes a
     * real DATETIME if the author says so, and a field the schema calls a DATETIME
     * becomes plain text if they say that instead. Reading the format would get both
     * backwards and the mistake would only show up as a value that fails validation on
     * the way back out.
     */
    public static Set<String> dateStringColumns(
            Schema storageSchema, Map<String, StorageColumnDefinition> definitions) {

        Set<String> out = new java.util.LinkedHashSet<>();
        for (MySQLColumn c : columns(storageSchema, definitions)) if (isTemporal(c.type())) out.add(c.name());
        return out;
    }

    /** The columns of this storage that hold JSON, by name. */
    public static Set<String> jsonColumns(Schema storageSchema) {
        return jsonColumns(storageSchema, null);
    }

    public static Set<String> jsonColumns(Schema storageSchema, Map<String, StorageColumnDefinition> definitions) {
        Set<String> out = new java.util.LinkedHashSet<>();
        for (MySQLColumn c : columns(storageSchema, definitions)) if (isJson(c.type())) out.add(c.name());
        return out;
    }

    /**
     * The columns that come back from the driver as a BigDecimal.
     *
     * Needed for the same reason the date columns are. A field declared STRING with
     * format DECIMAL is stored as a real DECIMAL - which is the entire point, since a
     * text column cannot be summed - but the API contract still says string, and the
     * validator will reject the BigDecimal the driver hands back. Read a row, change
     * one unrelated field, write it back, and the write fails on a value the caller
     * never touched.
     */
    public static Set<String> decimalColumns(
            Schema storageSchema, Map<String, StorageColumnDefinition> definitions) {

        Set<String> out = new java.util.LinkedHashSet<>();
        for (MySQLColumn c : columns(storageSchema, definitions)) if (isDecimal(c.type())) out.add(c.name());
        return out;
    }

    public static boolean isDecimal(String columnType) {
        return columnType != null && columnType.toUpperCase().startsWith("DECIMAL");
    }

    static boolean isTemporal(String columnType) {
        if (columnType == null) return false;
        String t = columnType.toUpperCase();
        return t.startsWith("DATETIME") || t.startsWith("TIMESTAMP") || t.startsWith("TIME") || t.equals("DATE");
    }

    /**
     * A declared date becomes a real date column, which is the single biggest reason
     * to put app data on a relational backend.
     *
     * On the Mongo backend the same field is an untyped epoch number, which is why
     * aggregation there has to be told the encoding (EPOCH_SECONDS or EPOCH_MILLIS)
     * and guard against the two being confused. Here the engine knows, and that whole
     * class of mistake cannot happen.
     */
    private static MySQLColumn stringColumn(String name, Schema fieldSchema, boolean nullable) {

        StringFormat format = fieldSchema.getFormat();
        if (format != null) {
            switch (format) {
                case DATETIME:
                    return new MySQLColumn(name, "DATETIME(3)", nullable);
                case DATE:
                    return new MySQLColumn(name, "DATE", nullable);
                case TIME:
                    return new MySQLColumn(name, "TIME(3)", nullable);
                case ID:
                    // Fixed width, so it indexes and joins like the primary key it
                    // points at. A ULID is 26 characters; a Mongo ObjectId is 24 and
                    // compares equal in CHAR because trailing spaces are ignored.
                    return new MySQLColumn(name, ID_TYPE, nullable);
                case DECIMAL:
                    return new MySQLColumn(name, DEFAULT_DECIMAL, nullable, "stored as " + DEFAULT_DECIMAL
                            + ", the conventional shape for money. Declare maxLength for more total digits;"
                            + " the scale is not yet configurable.");
                default:
                    break;
            }
        }

        Integer max = fieldSchema.getMaxLength() != null ? fieldSchema.getMaxLength() : impliedLength(fieldSchema);

        if (max == null)
            return new MySQLColumn(
                    name,
                    "TEXT",
                    nullable,
                    "no maxLength, so this becomes TEXT and cannot be indexed, joined on, or"
                            + " given a default. Declare maxLength to get a VARCHAR.");

        if (max <= 0)
            return new MySQLColumn(name, "VARCHAR(" + DEFAULT_VARCHAR + ")", nullable, "maxLength " + max
                    + " is not usable, defaulted to " + DEFAULT_VARCHAR);

        if (max > MAX_VARCHAR)
            return new MySQLColumn(
                    name, "TEXT", nullable, "maxLength " + max + " exceeds " + MAX_VARCHAR + ", so TEXT is used");

        return new MySQLColumn(name, "VARCHAR(" + max + ")", nullable);
    }

    /**
     * A length the schema states without saying maxLength.
     *
     * An enum's longest value and an email address's ceiling are both known, and
     * falling through to TEXT for them would make the two most commonly filtered and
     * joined kinds of string the two that cannot be indexed.
     */
    private static Integer impliedLength(Schema fieldSchema) {

        if (fieldSchema.getEnums() != null && !fieldSchema.getEnums().isEmpty()) {
            int longest = 1;
            for (com.google.gson.JsonElement e : fieldSchema.getEnums()) {
                if (e == null || !e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString()) return null;
                longest = Math.max(longest, e.getAsString().length());
            }
            return longest;
        }

        // RFC 5321: 64 for the local part, 1 for the @, 255 for the domain.
        if (fieldSchema.getFormat() == StringFormat.EMAIL) return EMAIL_LENGTH;

        return null;
    }

    private static Set<SchemaType> allowedTypes(Schema fieldSchema) {
        if (fieldSchema == null || fieldSchema.getType() == null) return Set.of();
        Set<SchemaType> t = fieldSchema.getType().getAllowedSchemaTypes();
        return t == null ? Set.of() : t;
    }
}
