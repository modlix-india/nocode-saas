package com.fincity.saas.commons.core.service.connection.appdata.mysql;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Generates the DDL for a storage table, and classifies what changes between two
 * versions of its schema.
 *
 * Pure, so the classification can be tested exhaustively without a database. That
 * matters more here than anywhere else in this backend: the classifier is what decides
 * whether a schema edit applies silently or is held back for a data check, and getting
 * it wrong in the permissive direction is how a publish destroys a tenant's column.
 */
public final class MySQLTablePlanner {

    /** Widening a VARCHAR, or promoting it to TEXT, cannot lose data. */
    private static final Pattern VARCHAR = Pattern.compile("^VARCHAR\\((\\d+)\\)$", Pattern.CASE_INSENSITIVE);

    /** Integer widenings, narrow to wide. A move right along this list is safe. */
    private static final List<String> INT_WIDTHS = List.of("TINYINT(1)", "INT", "BIGINT");

    private MySQLTablePlanner() {
    }

    /**
     * A short, stable fingerprint of the shape a table is supposed to have.
     *
     * This is the migration's identity, and the storage's version number cannot be.
     * A version only moves when the STORAGE is edited, and the table's shape also
     * changes when a schema the storage references is edited, or when a client's
     * override is. Keying on the version means a schema edit finds a row already
     * marked applied and quietly does nothing at all - the table stays on the old
     * shape and the first write of the wrong type is where anyone finds out.
     *
     * Per tenant, because the definition is overridable and each client resolves to a
     * different table.
     */
    public static String shapeOf(List<MySQLColumn> desired) {

        StringBuilder sb = new StringBuilder();
        if (desired != null)
            desired.stream()
                    .sorted(java.util.Comparator.comparing(MySQLColumn::name))
                    .forEach(c -> sb.append(c.name())
                            .append(' ')
                            .append(c.type().toUpperCase())
                            .append(c.nullable() ? " NULL" : " NOT NULL")
                            // Only when declared. A null collation means the author
                            // did not ask, so the table default is correct by
                            // definition and must not make the shape look different.
                            .append(c.collation() == null ? "" : " COLLATE " + c.collation().toUpperCase())
                            .append('\n'));

        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < 16; i++) hex.append(String.format("%02x", digest[i]));
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    public static String createTable(String table, List<MySQLColumn> columns) {

        StringBuilder sb = new StringBuilder("CREATE TABLE IF NOT EXISTS `").append(table).append("` (\n  `")
                .append(MySQLTypeMapper.ID_COLUMN)
                .append("` ")
                .append(MySQLTypeMapper.ID_TYPE)
                .append(" NOT NULL");

        for (MySQLColumn c : columns) sb.append(",\n  ").append(c.ddl());

        sb.append(",\n  PRIMARY KEY (`").append(MySQLTypeMapper.ID_COLUMN).append("`)\n)");
        return sb.toString();
    }

    /**
     * What has to happen to turn {@code existing} into {@code desired}.
     *
     * Order is deliberate: additions first, then alterations, then drops. A plan that
     * drops before it adds cannot be abandoned half way without data loss, and the
     * whole recovery story depends on being able to stop at any point and still have a
     * working table.
     */
    public static List<SchemaChange> diff(List<MySQLColumn> existing, List<MySQLColumn> desired) {

        Map<String, MySQLColumn> before = byName(existing);
        Map<String, MySQLColumn> after = byName(desired);

        List<SchemaChange> adds = new ArrayList<>();
        List<SchemaChange> alters = new ArrayList<>();
        List<SchemaChange> drops = new ArrayList<>();

        for (MySQLColumn d : desired) {
            MySQLColumn e = before.get(d.name());

            if (e == null) {
                adds.add(d.nullable()
                        ? new SchemaChange(
                                SchemaChange.Kind.WIDENING,
                                d.name(),
                                null,
                                d.type(),
                                "new nullable column",
                                d.collation())
                        : new SchemaChange(
                                SchemaChange.Kind.NARROWING,
                                d.name(),
                                null,
                                d.type(),
                                "new NOT NULL column; existing rows have no value for it",
                                d.collation()));
                continue;
            }

            SchemaChange c = compare(e, d);
            if (c != null) alters.add(c);
        }

        for (MySQLColumn e : existing)
            if (!after.containsKey(e.name()))
                drops.add(new SchemaChange(
                        SchemaChange.Kind.DESTRUCTIVE, e.name(), e.type(), null, "column removed from the schema"));

        List<SchemaChange> all = new ArrayList<>(adds);
        all.addAll(alters);
        all.addAll(drops);
        return all;
    }

    private static SchemaChange compare(MySQLColumn before, MySQLColumn after) {

        boolean typeChanged = !before.type().equalsIgnoreCase(after.type());
        boolean tightened = before.nullable() && !after.nullable();
        boolean loosened = !before.nullable() && after.nullable();

        // Asymmetric on purpose. A column with no declared collation is compared on
        // type and nullability alone, because MySQL always reports SOME collation for
        // a text column - the table default - and treating that as a difference would
        // have the reconciler alter every text column on the very first run and then
        // again on every run after it.
        boolean collationChanged =
                after.collation() != null && !after.collation().equalsIgnoreCase(before.collation());

        if (!typeChanged && !tightened && !loosened && !collationChanged) return null;

        // A collation change rewrites how values sort and compare, but no stored value
        // can fail to convert, so it is a widening even though it touches every row.
        if (typeChanged && !isSafeWidening(before.type(), after.type()))
            return new SchemaChange(
                    SchemaChange.Kind.NARROWING,
                    after.name(),
                    before.type(),
                    after.type(),
                    "type change that existing values may not satisfy",
                    after.collation());

        if (tightened)
            return new SchemaChange(
                    SchemaChange.Kind.NARROWING,
                    after.name(),
                    before.type(),
                    after.type(),
                    "now NOT NULL; existing rows may hold null",
                    after.collation());

        String reason;
        if (typeChanged) reason = "widening type change";
        else if (collationChanged) reason = "collation change to " + after.collation();
        else reason = "now nullable";

        return new SchemaChange(
                SchemaChange.Kind.WIDENING, after.name(), before.type(), after.type(), reason, after.collation());
    }

    /**
     * True only for conversions where no stored value can fail.
     *
     * Deliberately conservative. Anything not recognised here is treated as narrowing
     * and gets a data check, which costs one counting query. The opposite mistake,
     * calling a lossy change safe, costs a column.
     */
    static boolean isSafeWidening(String from, String to) {

        if (from.equalsIgnoreCase(to)) return true;

        Matcher f = VARCHAR.matcher(from);
        Matcher t = VARCHAR.matcher(to);

        if (f.matches() && t.matches()) return Integer.parseInt(t.group(1)) >= Integer.parseInt(f.group(1));

        // Any VARCHAR fits in TEXT.
        if (f.matches() && "TEXT".equalsIgnoreCase(to)) return true;

        int fi = indexOfType(INT_WIDTHS, from);
        int ti = indexOfType(INT_WIDTHS, to);
        if (fi >= 0 && ti >= 0) return ti >= fi;

        // Every INT fits in a DOUBLE, and FLOAT fits in DOUBLE.
        if (fi >= 0 && "DOUBLE".equalsIgnoreCase(to)) return true;
        return "FLOAT".equalsIgnoreCase(from) && "DOUBLE".equalsIgnoreCase(to);
    }

    private static int indexOfType(List<String> widths, String type) {
        for (int i = 0; i < widths.size(); i++) if (widths.get(i).equalsIgnoreCase(type)) return i;
        return -1;
    }

    private static Map<String, MySQLColumn> byName(List<MySQLColumn> cols) {
        Map<String, MySQLColumn> m = new LinkedHashMap<>();
        if (cols != null) for (MySQLColumn c : cols) m.put(c.name(), c);
        return m;
    }
}
