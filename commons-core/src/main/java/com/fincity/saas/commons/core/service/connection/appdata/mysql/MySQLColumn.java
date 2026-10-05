package com.fincity.saas.commons.core.service.connection.appdata.mysql;

import com.fincity.saas.commons.core.service.connection.appdata.StorageFieldNames;

/**
 * One column of a storage table, resolved from the storage's KIRun schema and
 * whatever the definition says about how it should physically be stored.
 *
 * @param name      the field name, used verbatim as the column name
 * @param type      the MySQL column type, e.g. {@code VARCHAR(120)}, rendered to match
 *                  what {@code information_schema.COLUMN_TYPE} reports back so the
 *                  migration diff can compare the two as text
 * @param nullable  false when the field is in the schema's {@code required} list
 * @param note      a caveat the author should see before this lands, or null. Not an
 *                  error: the column is still valid, but something about it is worth
 *                  knowing, such as a string with no declared length becoming TEXT and
 *                  therefore not being indexable or joinable.
 * @param collation the collation this column must have, or null for "whatever the
 *                  table uses". Separate from {@code type} because MySQL does NOT
 *                  report it in COLUMN_TYPE: folded into the type string it would
 *                  never match what comes back, and the reconciler would alter the
 *                  table on every single run. Null means not asked for rather than
 *                  "no collation", so an undeclared column never diffs against the
 *                  server default.
 */
public record MySQLColumn(String name, String type, boolean nullable, String note, String collation) {

    /**
     * The last gate before a name becomes DDL.
     *
     * A column name cannot be bound as a parameter, so it is concatenated between
     * backticks and a name containing one closes the quoting. StorageService refuses
     * such a field at save, which is where an author gets a usable error; this is
     * here because that is one path and this is the only one, and a check that lives
     * only at the boundary is a check that the next entry point forgets.
     */
    public MySQLColumn {
        if (!StorageFieldNames.valid(name))
            throw new IllegalArgumentException("not a usable column name: " + name);
    }

    public MySQLColumn(String name, String type, boolean nullable) {
        this(name, type, nullable, null, null);
    }

    public MySQLColumn(String name, String type, boolean nullable, String note) {
        this(name, type, nullable, note, null);
    }

    public MySQLColumn withCollation(String collation) {
        return new MySQLColumn(this.name, this.type, this.nullable, this.note, collation);
    }

    public MySQLColumn withNote(String note) {
        return new MySQLColumn(this.name, this.type, this.nullable, note, this.collation);
    }

    /** The fragment this column contributes to a CREATE TABLE or ADD COLUMN. */
    public String ddl() {
        return "`" + this.name + "` " + this.type
                + (this.collation == null ? "" : " COLLATE " + this.collation)
                + (this.nullable ? " NULL" : " NOT NULL");
    }
}
