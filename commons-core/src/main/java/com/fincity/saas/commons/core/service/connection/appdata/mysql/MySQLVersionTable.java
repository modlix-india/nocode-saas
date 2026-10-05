package com.fincity.saas.commons.core.service.connection.appdata.mysql;

import java.util.List;

/**
 * The history table behind a versioned or audited storage.
 *
 * Column names match the Mongo version collection exactly. A caller reading history
 * should not be able to tell which backend their app is on, and the two drifting
 * apart would only be discovered by whoever needed the history, at the moment they
 * needed it.
 */
public final class MySQLVersionTable {

    public static final String SUFFIX = "_version";

    public static final String OBJECT_ID = "objectId";
    public static final String MESSAGE = "message";
    public static final String CREATED_AT = "createdAt";
    public static final String OPERATION = "operation";
    public static final String CREATED_BY = "createdBy";
    public static final String OBJECT = "object";

    /** Everything except the snapshot, for a read that only wants the audit trail. */
    public static final List<String> AUDIT_FIELDS =
            List.of(MySQLTypeMapper.ID_COLUMN, OBJECT_ID, MESSAGE, CREATED_AT, OPERATION, CREATED_BY);

    private MySQLVersionTable() {
    }

    public static String nameFor(String table) {
        return table + SUFFIX;
    }

    public static String createTable(String db, String table) {
        return "CREATE TABLE IF NOT EXISTS `" + db + "`.`" + nameFor(table) + "` ("
                + "`" + MySQLTypeMapper.ID_COLUMN + "` " + MySQLTypeMapper.ID_TYPE + " NOT NULL,"
                + "`" + OBJECT_ID + "` " + MySQLTypeMapper.ID_TYPE + " NOT NULL,"
                + "`" + MESSAGE + "` TEXT NULL,"
                + "`" + CREATED_AT + "` DATETIME(3) NOT NULL,"
                + "`" + OPERATION + "` VARCHAR(16) NOT NULL,"
                + "`" + CREATED_BY + "` BIGINT NULL,"
                // Only written when the storage asked to be VERSIONED. An audited
                // storage records who did what and when, and storing the body for it
                // would multiply the tenant's data size by its number of edits.
                + "`" + OBJECT + "` JSON NULL,"
                + "PRIMARY KEY (`" + MySQLTypeMapper.ID_COLUMN + "`),"
                // Every history read is "this row, newest first". Without this index
                // it is a full scan of the whole table's history, which is the one
                // table guaranteed to be larger than the data it describes.
                + "KEY `idx_object_history` (`" + OBJECT_ID + "`, `" + CREATED_AT + "` DESC)"
                + ")";
    }
}
