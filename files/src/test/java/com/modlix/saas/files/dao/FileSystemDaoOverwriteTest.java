package com.modlix.saas.files.dao;

import static com.modlix.saas.files.jooq.tables.FilesFileSystem.FILES_FILE_SYSTEM;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.jooq.DSLContext;
import org.jooq.Result;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.MockConnection;
import org.jooq.tools.jdbc.MockExecuteContext;
import org.jooq.tools.jdbc.MockResult;
import org.jooq.types.ULong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.modlix.saas.files.jooq.enums.FilesFileSystemFileType;
import com.modlix.saas.files.jooq.enums.FilesFileSystemType;
import com.modlix.saas.files.jooq.tables.records.FilesFileSystemRecord;
import com.modlix.saas.files.model.FileDetail;

/**
 * Overwriting a file at the same path must update its record: the new size, a fresh UPDATED_AT
 * from the database clock, and only the row of that client and file system type.
 */
class FileSystemDaoOverwriteTest {

    private static final ULong FOLDER_ID = ULong.valueOf(10);
    private static final ULong FILE_ID = ULong.valueOf(11);

    private final List<String> sql = new ArrayList<>();
    private final List<List<Object>> binds = new ArrayList<>();
    private int updatedRows;
    private long storedSize;

    private FileSystemDao dao;

    @BeforeEach
    void setUp() {
        storedSize = 156;
        updatedRows = 1;
        DSLContext mockCtx = DSL.using(SQLDialect.MYSQL);

        MockConnection connection = new MockConnection((MockExecuteContext ctx) -> {

            String statement = ctx.sql();
            sql.add(statement);
            binds.add(Arrays.asList(ctx.bindings()));

            String lower = statement.toLowerCase();

            if (lower.startsWith("update")) {
                // UPDATED_AT is inlined as current_timestamp(), so SIZE is the first placeholder.
                Object first = ctx.bindings().length == 0 ? null : ctx.bindings()[0];
                if (first != null)
                    storedSize = Long.parseLong(first.toString());
                return new MockResult[] { new MockResult(updatedRows, null) };
            }

            if (lower.startsWith("insert"))
                return new MockResult[] { new MockResult(1, null) };

            Result<FilesFileSystemRecord> result = mockCtx.newResult(FILES_FILE_SYSTEM);
            result.add(record(FOLDER_ID, null, "_userImages", FilesFileSystemFileType.DIRECTORY, null));
            result.add(record(FILE_ID, FOLDER_ID, "142.png", FilesFileSystemFileType.FILE,
                    ULong.valueOf(storedSize)));
            return new MockResult[] { new MockResult(result.size(), result) };
        });

        dao = new FileSystemDao(DSL.using(connection, SQLDialect.MYSQL));
    }

    private static FilesFileSystemRecord record(ULong id, ULong parentId, String name,
            FilesFileSystemFileType fileType, ULong size) {

        FilesFileSystemRecord rec = new FilesFileSystemRecord();
        rec.setId(id);
        rec.setParentId(parentId);
        rec.setName(name);
        rec.setFileType(fileType);
        rec.setType(FilesFileSystemType.SECURED);
        rec.setCode("SYSTEM");
        rec.setSize(size);
        rec.setCreatedAt(LocalDateTime.of(2026, 10, 4, 16, 31, 14));
        rec.setUpdatedAt(LocalDateTime.of(2026, 10, 4, 16, 40, 0));
        return rec;
    }

    private String updateStatement() {
        return sql.stream().filter(s -> s.toLowerCase().startsWith("update")).findFirst().orElse(null);
    }

    private List<Object> updateBindings() {
        for (int i = 0; i < sql.size(); i++)
            if (sql.get(i).toLowerCase().startsWith("update"))
                return binds.get(i);
        return List.of();
    }

    @Test
    void overwriteStoresTheNewSizeAndReturnsIt() {

        FileDetail fd = dao.createOrUpdateFile(FilesFileSystemType.SECURED, "SYSTEM", "_userImages/142.png",
                "142.png", ULong.valueOf(110), true);

        assertNotNull(fd);
        assertEquals(110L, fd.getSize(), "the response must carry the size just written");

        String update = updateStatement();
        assertNotNull(update, "an overwrite updates the existing row");
        assertTrue(update.contains("`SIZE` = ?"), update);
        assertEquals("110", String.valueOf(updateBindings().get(0)), "SIZE is bound to the new length");
    }

    @Test
    void overwriteTakesUpdatedAtFromTheDatabaseClock() {

        dao.createOrUpdateFile(FilesFileSystemType.SECURED, "SYSTEM", "_userImages/142.png", "142.png",
                ULong.valueOf(110), true);

        String update = updateStatement();
        assertTrue(update.contains("`UPDATED_AT` = current_timestamp()"), update);
        assertFalse(updateBindings().stream().anyMatch(LocalDateTime.class::isInstance),
                "no JVM-side timestamp is bound, so no time zone conversion can shift it");
    }

    @Test
    void overwriteIsScopedToTheClientAndFileSystemType() {

        dao.createOrUpdateFile(FilesFileSystemType.SECURED, "SYSTEM", "_userImages/142.png", "142.png",
                ULong.valueOf(110), true);

        String update = updateStatement();
        assertTrue(update.contains("`CODE` = ?"), update);
        assertTrue(update.contains("`TYPE` = ?"), update);
        assertTrue(updateBindings().contains("SYSTEM"));
        assertTrue(updateBindings().contains(FilesFileSystemType.SECURED.getLiteral())
                || updateBindings().contains(FilesFileSystemType.SECURED));
    }

    @Test
    void anOverwriteThatMatchesNoRowInsertsOne() {

        updatedRows = 0;

        FileDetail fd = dao.createOrUpdateFile(FilesFileSystemType.SECURED, "SYSTEM", "_userImages/142.png",
                "142.png", ULong.valueOf(110), true);

        assertNotNull(fd);
        assertTrue(sql.stream().anyMatch(s -> s.toLowerCase().startsWith("insert")),
                "a stale exists answer must not leave the uploaded object without a record");
    }

    @Test
    void storedTimesAreReadInTheJvmZoneTheDriverConvertedThemTo() {

        // What the driver hands back for a row written at 16:34:38 UTC: that instant as wall time
        // in the JVM's zone (22:04:38 on an IST machine, 16:34:38 on a UTC server).
        java.time.Instant written = java.time.Instant.parse("2026-10-04T16:34:38Z");
        LocalDateTime fromDriver = LocalDateTime.ofInstant(written, java.time.ZoneId.systemDefault());

        assertEquals(written.getEpochSecond(), FileSystemDao.epochSeconds(fromDriver));
        assertEquals(0L, FileSystemDao.epochSeconds(null));
    }
}
