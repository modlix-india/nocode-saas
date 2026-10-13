package com.modlix.saas.files.dao;

import static com.modlix.saas.files.jooq.tables.FilesFileSystem.FILES_FILE_SYSTEM;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record2;
import org.jooq.Result;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.jooq.tools.jdbc.MockConnection;
import org.jooq.tools.jdbc.MockExecuteContext;
import org.jooq.tools.jdbc.MockResult;
import org.jooq.types.ULong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.modlix.saas.files.jooq.enums.FilesFileSystemType;

/**
 * The lifetime cleanup reads one file system type at a time, and the retention sweep only ever
 * selects files without their own lifetime, inside one client's folder.
 */
class FileSystemDaoRetentionTest {

    private final List<String> sql = new ArrayList<>();
    private final List<List<Object>> binds = new ArrayList<>();
    private DSLContext mockCtx;
    private FileSystemDao dao;

    @BeforeEach
    void setUp() {
        mockCtx = DSL.using(SQLDialect.MYSQL);

        MockConnection connection = new MockConnection((MockExecuteContext ctx) -> {
            sql.add(ctx.sql());
            binds.add(Arrays.asList(ctx.bindings()));

            String lower = ctx.sql().toLowerCase();

            if (lower.startsWith("with recursive")) {
                Field<ULong> id = DSL.field("ID", SQLDataType.BIGINTUNSIGNED);
                Field<String> rel = DSL.field("REL_PATH", SQLDataType.VARCHAR);
                Result<Record2<ULong, String>> r = mockCtx.newResult(id, rel);
                r.add(mockCtx.newRecord(id, rel).values(ULong.valueOf(7), "/sub/a.jpg"));
                return new MockResult[] { new MockResult(1, r) };
            }

            return new MockResult[] { new MockResult(0, mockCtx.newResult(FILES_FILE_SYSTEM.ID, FILES_FILE_SYSTEM.CODE,
                    FILES_FILE_SYSTEM.TYPE, FILES_FILE_SYSTEM.NAME, FILES_FILE_SYSTEM.PARENT_ID)) };
        });

        dao = new FileSystemDao(DSL.using(connection, SQLDialect.MYSQL));
    }

    @Test
    void readExpiredIsScopedToTheFileSystemType() {

        dao.readExpired(FilesFileSystemType.SECURED, 50);

        String statement = sql.getFirst();
        assertTrue(statement.contains("`files_file_system`.`TYPE` = ?"), statement);
        assertTrue(statement.contains("`EXPIRES_AFTER_MINUTES` is not null"), statement);
        assertTrue(binds.getFirst().contains("SECURED"), binds.getFirst().toString());
        assertTrue(!binds.getFirst().contains("STATIC"), binds.getFirst().toString());
    }

    @Test
    void retentionSelectionSkipsFilesWithTheirOwnLifetimeAndStaysInTheClient() {

        List<FileSystemDao.ExpiredFile> files = dao.readRetentionExpired(FilesFileSystemType.STATIC, "C1",
                ULong.valueOf(100), "/app/whatsapp/incoming", List.of(ULong.valueOf(101), ULong.valueOf(102)), 30,
                25);

        String statement = sql.getFirst();
        assertTrue(statement.contains("EXPIRES_AFTER_MINUTES IS NULL"), statement);
        assertTrue(statement.contains("FILE_TYPE = 'FILE'"), statement);
        assertTrue(statement.contains("c.CODE = ? AND c.TYPE = ?"), statement);
        assertTrue(statement.contains("c.ID NOT IN (?, ?)"), statement);
        assertTrue(statement.contains("INTERVAL ? DAY"), statement);

        // Compared as text: the driver-level bind types (ULong as BigInteger and so on) do not matter here.
        assertEquals(List.of("100", "C1", "STATIC", "C1", "STATIC", "101", "102", "30", "25"),
                binds.getFirst().stream().map(String::valueOf).toList());

        assertEquals(1, files.size());
        assertEquals("/app/whatsapp/incoming/sub/a.jpg", files.getFirst().path());
        assertEquals("C1", files.getFirst().clientCode());
        assertEquals(FilesFileSystemType.STATIC, files.getFirst().type());
    }

    @Test
    void retentionSelectionWithZeroLimitReadsNothing() {
        assertTrue(dao.readRetentionExpired(FilesFileSystemType.STATIC, "C1", ULong.valueOf(1), "/app", List.of(), 30, 0)
                .isEmpty());
        assertTrue(sql.isEmpty());
    }
}
