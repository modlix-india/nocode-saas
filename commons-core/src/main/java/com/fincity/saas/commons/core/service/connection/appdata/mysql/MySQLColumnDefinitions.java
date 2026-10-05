package com.fincity.saas.commons.core.service.connection.appdata.mysql;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import com.fincity.saas.commons.core.enums.MySQLColumnType;
import com.fincity.saas.commons.core.model.StorageColumnDefinition;

/**
 * Renders and checks the MySQL half of a column definition.
 *
 * Pure, and the rendering is the only path from an author-supplied definition into
 * DDL. That is the point: a column type cannot be bound as a parameter, so the single
 * defence against a storage definition carrying SQL into {@code ALTER TABLE} is that
 * nothing author-supplied is ever concatenated verbatim. The type comes from an enum,
 * every argument is parsed as an int and re-rendered, and the collation is matched
 * against a character class before it is used. A definition that cannot be rendered
 * this way is refused at save rather than sanitised.
 */
public final class MySQLColumnDefinitions {

    /** Collation names are identifiers; anything else is not a collation. */
    private static final Pattern COLLATION = Pattern.compile("^[A-Za-z0-9_]{1,64}$");

    /** CHAR and BINARY are capped by MySQL itself, far below the VARCHAR ceiling. */
    private static final int MAX_FIXED_LENGTH = 255;

    private static final int MAX_SCALE = 30;

    private MySQLColumnDefinitions() {
    }

    /**
     * The column type as MySQL will report it back.
     *
     * Rendered to match {@code information_schema.COLUMN_TYPE} rather than to be
     * pretty, because the migration diff compares the two as text. {@code UNSIGNED}
     * is part of COLUMN_TYPE and so belongs here; a collation is not, and is carried
     * separately on {@link MySQLColumn} for exactly that reason.
     */
    public static String type(StorageColumnDefinition.MySQL def) {

        MySQLColumnType type = def.getType();
        StringBuilder sb = new StringBuilder(type.name());

        switch (type.arity()) {
            case LENGTH -> sb.append('(').append(def.getLength().intValue()).append(')');
            case PRECISION_SCALE -> sb.append('(')
                    .append(def.getPrecision().intValue())
                    .append(',')
                    .append(def.getScale().intValue())
                    .append(')');
            case FRACTIONAL_SECONDS -> {
                if (def.getPrecision() != null && def.getPrecision() > 0)
                    sb.append('(').append(def.getPrecision().intValue()).append(')');
            }
            case NONE -> {
                // No arguments by construction.
            }
        }

        if (Boolean.TRUE.equals(def.getUnsigned()) && type.numeric()) sb.append(" UNSIGNED");

        return sb.toString();
    }

    /** The collation to apply, or null. Only ever a value that passed {@link #COLLATION}. */
    public static String collation(StorageColumnDefinition.MySQL def) {

        if (def == null || def.getCollation() == null || def.getCollation().isBlank()) return null;
        return COLLATION.matcher(def.getCollation()).matches() ? def.getCollation() : null;
    }

    /**
     * Everything wrong with this definition, in words an author can act on.
     *
     * A list rather than the first failure, because a half-fixed definition that fails
     * again on save is a worse experience than one message naming all three problems.
     */
    public static List<String> problems(String field, StorageColumnDefinition.MySQL def) {
        return problems(field, def, true);
    }

    /**
     * @param requireType false when this is a derived storage's DELTA rather than a
     *                    whole definition. A client that changes only the length
     *                    stores just the length - the type is identical to the base
     *                    and the difference extractor drops it - so demanding one
     *                    here refused every narrowing of an inherited column. The
     *                    merged definition still has a type; this document simply
     *                    does not repeat it.
     */
    public static List<String> problems(String field, StorageColumnDefinition.MySQL def, boolean requireType) {

        List<String> out = new ArrayList<>();
        if (def == null) return out;

        MySQLColumnType type = def.getType();
        if (type == null) {
            // Nothing after this can be checked without knowing the type: every
            // arity rule is a property of it.
            if (requireType)
                out.add(field + ": mysql.type is required when a mysql column definition is given");
            return out;
        }

        switch (type.arity()) {
            case LENGTH -> checkLength(field, def, type, out);
            case PRECISION_SCALE -> checkPrecisionScale(field, def, type, out);
            case FRACTIONAL_SECONDS -> checkFractionalSeconds(field, def, type, out);
            case NONE -> {
                if (def.getLength() != null) out.add(field + ": " + type + " takes no length");
                if (def.getPrecision() != null) out.add(field + ": " + type + " takes no precision");
                if (def.getScale() != null) out.add(field + ": " + type + " takes no scale");
            }
        }

        if (Boolean.TRUE.equals(def.getUnsigned()) && !type.numeric())
            out.add(field + ": unsigned means nothing for " + type);

        if (def.getCollation() != null && !def.getCollation().isBlank()) {
            if (!type.textual())
                out.add(field + ": a collation applies only to a text column, and " + type + " is not one");
            else if (!COLLATION.matcher(def.getCollation()).matches())
                out.add(field + ": " + def.getCollation() + " is not a collation name");
        }

        return out;
    }

    private static void checkLength(
            String field, StorageColumnDefinition.MySQL def, MySQLColumnType type, List<String> out) {

        if (def.getScale() != null) out.add(field + ": " + type + " takes no scale");

        if (def.getLength() == null) {
            // Not pedantry. MySQL would silently store CHAR(1) and report it back on
            // the next reconcile as a difference, so the table would be altered on
            // every publish for ever.
            out.add(field + ": " + type + " needs a length, because MySQL defaults it invisibly");
            return;
        }

        int max = type == MySQLColumnType.CHAR || type == MySQLColumnType.BINARY
                ? MAX_FIXED_LENGTH
                : MySQLColumnType.MAX_VARCHAR;

        if (def.getLength() < 1 || def.getLength() > max)
            out.add(field + ": " + type + " length must be between 1 and " + max + ", not " + def.getLength());
    }

    private static void checkPrecisionScale(
            String field, StorageColumnDefinition.MySQL def, MySQLColumnType type, List<String> out) {

        if (def.getLength() != null) out.add(field + ": " + type + " takes no length");

        if (def.getPrecision() == null || def.getScale() == null) {
            out.add(field + ": " + type + " needs both a precision and a scale, because MySQL"
                    + " defaults them to (10,0) invisibly and would drop every fractional digit");
            return;
        }

        if (def.getPrecision() < 1 || def.getPrecision() > MySQLColumnType.MAX_PRECISION)
            out.add(field + ": precision must be between 1 and " + MySQLColumnType.MAX_PRECISION + ", not "
                    + def.getPrecision());

        if (def.getScale() < 0 || def.getScale() > MAX_SCALE)
            out.add(field + ": scale must be between 0 and " + MAX_SCALE + ", not " + def.getScale());

        if (def.getPrecision() >= 1 && def.getScale() >= 0 && def.getScale() > def.getPrecision())
            out.add(field + ": scale " + def.getScale() + " cannot exceed precision " + def.getPrecision());
    }

    private static void checkFractionalSeconds(
            String field, StorageColumnDefinition.MySQL def, MySQLColumnType type, List<String> out) {

        if (def.getLength() != null) out.add(field + ": " + type + " takes no length");
        if (def.getScale() != null) out.add(field + ": " + type + " takes no scale");

        if (def.getPrecision() != null
                && (def.getPrecision() < 0 || def.getPrecision() > MySQLColumnType.MAX_FRACTIONAL_SECONDS))
            out.add(field + ": " + type + " fractional seconds must be between 0 and "
                    + MySQLColumnType.MAX_FRACTIONAL_SECONDS + ", not " + def.getPrecision());
    }
}
