package com.fincity.saas.commons.core.service.connection.appdata.mysql;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A SQL predicate that is true when a stored value will survive conversion to a type.
 *
 * Needed because MySQL in strict mode does not write NULL for a value it cannot
 * convert: it aborts the statement with "Data truncated". An expand-contract backfill
 * that simply copies the old column therefore dies on the first bad row rather than
 * leaving it behind to be counted, so the backfill has to select only the rows it knows
 * will convert, and the rest are what the check reports.
 *
 * The same predicate gives a PRE-FLIGHT check expressed purely over the original
 * column, which is what lets every tenant be checked before any of them is touched.
 * A check that references the temporary column can only run mid-migration, by which
 * point the decision to start has already been made.
 */
public final class MySQLConvertibility {

    private static final Pattern VARCHAR = Pattern.compile("^VARCHAR\\((\\d+)\\)$", Pattern.CASE_INSENSITIVE);

    private static final Pattern CHAR = Pattern.compile("^CHAR\\((\\d+)\\)$", Pattern.CASE_INSENSITIVE);

    /** Optional sign, digits, optional fraction, optional exponent. */
    private static final String DECIMAL_RE = "^[+-]?([0-9]+(\\\\.[0-9]*)?|\\\\.[0-9]+)([eE][+-]?[0-9]+)?$";

    private static final String INTEGER_RE = "^[+-]?[0-9]+$";

    /** Leading ISO date is enough of a guard; the mid-plan VERIFY catches the residue. */
    private static final String DATE_RE = "^[0-9]{4}-[0-9]{2}-[0-9]{2}";

    private MySQLConvertibility() {
    }

    /**
     * @return SQL that is true for a value convertible to {@code targetType}
     * @throws UnsupportedFilterException when no safe predicate is known, rather than
     *         guessing and letting the backfill abort on real data
     */
    public static String predicate(String column, String targetType) {

        String col = "`" + column + "`";
        String t = targetType == null ? "" : targetType.trim().toUpperCase(Locale.ROOT);

        switch (t) {
            case "TEXT", "LONGTEXT", "MEDIUMTEXT":
                return "1 = 1";
            case "INT", "BIGINT", "SMALLINT":
                return col + " REGEXP '" + INTEGER_RE + "'";
            case "TINYINT(1)":
                return col + " REGEXP '^[01]$'";
            case "DOUBLE", "FLOAT":
                return col + " REGEXP '" + DECIMAL_RE + "'";
            case "DATE", "DATETIME", "DATETIME(3)", "TIMESTAMP":
                return col + " REGEXP '" + DATE_RE + "'";
            case "JSON":
                return "JSON_VALID(" + col + ")";
            default:
                break;
        }

        Matcher v = VARCHAR.matcher(t);
        if (v.matches()) return "CHAR_LENGTH(" + col + ") <= " + v.group(1);

        Matcher c = CHAR.matcher(t);
        if (c.matches()) return "CHAR_LENGTH(" + col + ") <= " + c.group(1);

        if (t.startsWith("DECIMAL")) return col + " REGEXP '" + DECIMAL_RE + "'";

        throw new UnsupportedFilterException(
                "no safe convertibility check is known for target type " + targetType
                        + ", so this change cannot be migrated automatically");
    }

    /** Counts the rows that would NOT survive the change. Zero means it is safe to apply. */
    public static String preflight(String db, String table, String column, String targetType) {
        return "SELECT COUNT(*) FROM `" + db + "`.`" + table + "` WHERE `" + column + "` IS NOT NULL AND NOT ("
                + predicate(column, targetType) + ")";
    }
}
