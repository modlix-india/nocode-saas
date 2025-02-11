/*
 * This file is generated by jOOQ.
 */
package com.fincity.saas.files.jooq.tables;


import com.fincity.saas.files.jooq.Files;
import com.fincity.saas.files.jooq.Keys;
import com.fincity.saas.files.jooq.tables.records.FilesSecuredAccessKeysRecord;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.Identity;
import org.jooq.Name;
import org.jooq.PlainSQL;
import org.jooq.QueryPart;
import org.jooq.SQL;
import org.jooq.Schema;
import org.jooq.Select;
import org.jooq.Stringly;
import org.jooq.Table;
import org.jooq.TableField;
import org.jooq.TableOptions;
import org.jooq.UniqueKey;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.jooq.impl.TableImpl;
import org.jooq.types.ULong;


/**
 * This class is generated by jOOQ.
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes", "this-escape" })
public class FilesSecuredAccessKeys extends TableImpl<FilesSecuredAccessKeysRecord> {

    private static final long serialVersionUID = 1L;

    /**
     * The reference instance of <code>files.files_secured_access_keys</code>
     */
    public static final FilesSecuredAccessKeys FILES_SECURED_ACCESS_KEYS = new FilesSecuredAccessKeys();

    /**
     * The class holding records for this type
     */
    @Override
    public Class<FilesSecuredAccessKeysRecord> getRecordType() {
        return FilesSecuredAccessKeysRecord.class;
    }

    /**
     * The column <code>files.files_secured_access_keys.ID</code>. Primary key
     */
    public final TableField<FilesSecuredAccessKeysRecord, ULong> ID = createField(DSL.name("ID"), SQLDataType.BIGINTUNSIGNED.nullable(false).identity(true), this, "Primary key");

    /**
     * The column <code>files.files_secured_access_keys.PATH</code>. Path which
     * needs to be secured.
     */
    public final TableField<FilesSecuredAccessKeysRecord, String> PATH = createField(DSL.name("PATH"), SQLDataType.VARCHAR(1024).nullable(false), this, "Path which needs to be secured.");

    /**
     * The column <code>files.files_secured_access_keys.ACCESS_KEY</code>. Key
     * used for securing the file.
     */
    public final TableField<FilesSecuredAccessKeysRecord, String> ACCESS_KEY = createField(DSL.name("ACCESS_KEY"), SQLDataType.CHAR(15).nullable(false), this, "Key used for securing the file.");

    /**
     * The column <code>files.files_secured_access_keys.ACCESS_TILL</code>. Time
     * which the path can be accessed
     */
    public final TableField<FilesSecuredAccessKeysRecord, LocalDateTime> ACCESS_TILL = createField(DSL.name("ACCESS_TILL"), SQLDataType.LOCALDATETIME(0).nullable(false), this, "Time which the path can be accessed");

    /**
     * The column <code>files.files_secured_access_keys.ACCESS_LIMIT</code>.
     * Maximum times in which the file can be accessed
     */
    public final TableField<FilesSecuredAccessKeysRecord, ULong> ACCESS_LIMIT = createField(DSL.name("ACCESS_LIMIT"), SQLDataType.BIGINTUNSIGNED, this, "Maximum times in which the file can be accessed");

    /**
     * The column <code>files.files_secured_access_keys.ACCESSED_COUNT</code>.
     * Tracks count of file accessed
     */
    public final TableField<FilesSecuredAccessKeysRecord, ULong> ACCESSED_COUNT = createField(DSL.name("ACCESSED_COUNT"), SQLDataType.BIGINTUNSIGNED.nullable(false).defaultValue(DSL.inline("0", SQLDataType.BIGINTUNSIGNED)), this, "Tracks count of file accessed");

    /**
     * The column <code>files.files_secured_access_keys.CREATED_BY</code>. ID of
     * the user who created this row
     */
    public final TableField<FilesSecuredAccessKeysRecord, ULong> CREATED_BY = createField(DSL.name("CREATED_BY"), SQLDataType.BIGINTUNSIGNED, this, "ID of the user who created this row");

    /**
     * The column <code>files.files_secured_access_keys.CREATED_AT</code>. Time
     * when this row is created
     */
    public final TableField<FilesSecuredAccessKeysRecord, LocalDateTime> CREATED_AT = createField(DSL.name("CREATED_AT"), SQLDataType.LOCALDATETIME(0).nullable(false).defaultValue(DSL.field(DSL.raw("CURRENT_TIMESTAMP"), SQLDataType.LOCALDATETIME)), this, "Time when this row is created");

    private FilesSecuredAccessKeys(Name alias, Table<FilesSecuredAccessKeysRecord> aliased) {
        this(alias, aliased, (Field<?>[]) null, null);
    }

    private FilesSecuredAccessKeys(Name alias, Table<FilesSecuredAccessKeysRecord> aliased, Field<?>[] parameters, Condition where) {
        super(alias, null, aliased, parameters, DSL.comment(""), TableOptions.table(), where);
    }

    /**
     * Create an aliased <code>files.files_secured_access_keys</code> table
     * reference
     */
    public FilesSecuredAccessKeys(String alias) {
        this(DSL.name(alias), FILES_SECURED_ACCESS_KEYS);
    }

    /**
     * Create an aliased <code>files.files_secured_access_keys</code> table
     * reference
     */
    public FilesSecuredAccessKeys(Name alias) {
        this(alias, FILES_SECURED_ACCESS_KEYS);
    }

    /**
     * Create a <code>files.files_secured_access_keys</code> table reference
     */
    public FilesSecuredAccessKeys() {
        this(DSL.name("files_secured_access_keys"), null);
    }

    @Override
    public Schema getSchema() {
        return aliased() ? null : Files.FILES;
    }

    @Override
    public Identity<FilesSecuredAccessKeysRecord, ULong> getIdentity() {
        return (Identity<FilesSecuredAccessKeysRecord, ULong>) super.getIdentity();
    }

    @Override
    public UniqueKey<FilesSecuredAccessKeysRecord> getPrimaryKey() {
        return Keys.KEY_FILES_SECURED_ACCESS_KEYS_PRIMARY;
    }

    @Override
    public List<UniqueKey<FilesSecuredAccessKeysRecord>> getUniqueKeys() {
        return Arrays.asList(Keys.KEY_FILES_SECURED_ACCESS_KEYS_UK1_ACCESS_KEY);
    }

    @Override
    public FilesSecuredAccessKeys as(String alias) {
        return new FilesSecuredAccessKeys(DSL.name(alias), this);
    }

    @Override
    public FilesSecuredAccessKeys as(Name alias) {
        return new FilesSecuredAccessKeys(alias, this);
    }

    @Override
    public FilesSecuredAccessKeys as(Table<?> alias) {
        return new FilesSecuredAccessKeys(alias.getQualifiedName(), this);
    }

    /**
     * Rename this table
     */
    @Override
    public FilesSecuredAccessKeys rename(String name) {
        return new FilesSecuredAccessKeys(DSL.name(name), null);
    }

    /**
     * Rename this table
     */
    @Override
    public FilesSecuredAccessKeys rename(Name name) {
        return new FilesSecuredAccessKeys(name, null);
    }

    /**
     * Rename this table
     */
    @Override
    public FilesSecuredAccessKeys rename(Table<?> name) {
        return new FilesSecuredAccessKeys(name.getQualifiedName(), null);
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public FilesSecuredAccessKeys where(Condition condition) {
        return new FilesSecuredAccessKeys(getQualifiedName(), aliased() ? this : null, null, condition);
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public FilesSecuredAccessKeys where(Collection<? extends Condition> conditions) {
        return where(DSL.and(conditions));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public FilesSecuredAccessKeys where(Condition... conditions) {
        return where(DSL.and(conditions));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public FilesSecuredAccessKeys where(Field<Boolean> condition) {
        return where(DSL.condition(condition));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    @PlainSQL
    public FilesSecuredAccessKeys where(SQL condition) {
        return where(DSL.condition(condition));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    @PlainSQL
    public FilesSecuredAccessKeys where(@Stringly.SQL String condition) {
        return where(DSL.condition(condition));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    @PlainSQL
    public FilesSecuredAccessKeys where(@Stringly.SQL String condition, Object... binds) {
        return where(DSL.condition(condition, binds));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    @PlainSQL
    public FilesSecuredAccessKeys where(@Stringly.SQL String condition, QueryPart... parts) {
        return where(DSL.condition(condition, parts));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public FilesSecuredAccessKeys whereExists(Select<?> select) {
        return where(DSL.exists(select));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public FilesSecuredAccessKeys whereNotExists(Select<?> select) {
        return where(DSL.notExists(select));
    }
}
