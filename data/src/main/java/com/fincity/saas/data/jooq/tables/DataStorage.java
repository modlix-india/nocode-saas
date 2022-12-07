/*
 * This file is generated by jOOQ.
 */
package com.fincity.saas.data.jooq.tables;


import com.fincity.saas.data.jooq.Data;
import com.fincity.saas.data.jooq.Keys;
import com.fincity.saas.data.jooq.enums.DataStorageStatus;
import com.fincity.saas.data.jooq.tables.records.DataStorageRecord;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import org.jooq.Field;
import org.jooq.ForeignKey;
import org.jooq.Function17;
import org.jooq.Identity;
import org.jooq.Name;
import org.jooq.Record;
import org.jooq.Records;
import org.jooq.Row17;
import org.jooq.Schema;
import org.jooq.SelectField;
import org.jooq.Table;
import org.jooq.TableField;
import org.jooq.TableOptions;
import org.jooq.UniqueKey;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.jooq.impl.TableImpl;
import org.jooq.types.UByte;
import org.jooq.types.ULong;


/**
 * This class is generated by jOOQ.
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes" })
public class DataStorage extends TableImpl<DataStorageRecord> {

    private static final long serialVersionUID = 1L;

    /**
     * The reference instance of <code>data.data_storage</code>
     */
    public static final DataStorage DATA_STORAGE = new DataStorage();

    /**
     * The class holding records for this type
     */
    @Override
    public Class<DataStorageRecord> getRecordType() {
        return DataStorageRecord.class;
    }

    /**
     * The column <code>data.data_storage.ID</code>. Primary key
     */
    public final TableField<DataStorageRecord, ULong> ID = createField(DSL.name("ID"), SQLDataType.BIGINTUNSIGNED.nullable(false).identity(true), this, "Primary key");

    /**
     * The column <code>data.data_storage.APP_CODE</code>. Application code
     */
    public final TableField<DataStorageRecord, String> APP_CODE = createField(DSL.name("APP_CODE"), SQLDataType.CHAR(64).nullable(false), this, "Application code");

    /**
     * The column <code>data.data_storage.NAMESPACE</code>. Namespace
     */
    public final TableField<DataStorageRecord, String> NAMESPACE = createField(DSL.name("NAMESPACE"), SQLDataType.CHAR(64).nullable(false), this, "Namespace");

    /**
     * The column <code>data.data_storage.NAME</code>. Name
     */
    public final TableField<DataStorageRecord, String> NAME = createField(DSL.name("NAME"), SQLDataType.CHAR(32).nullable(false), this, "Name");

    /**
     * The column <code>data.data_storage.DB_NAME</code>. Database name where
     * the data is stored
     */
    public final TableField<DataStorageRecord, String> DB_NAME = createField(DSL.name("DB_NAME"), SQLDataType.CHAR(64), this, "Database name where the data is stored");

    /**
     * The column <code>data.data_storage.IS_VERSIONED</code>. Versioned if it
     * is true
     */
    public final TableField<DataStorageRecord, UByte> IS_VERSIONED = createField(DSL.name("IS_VERSIONED"), SQLDataType.TINYINTUNSIGNED.nullable(false).defaultValue(DSL.inline("0", SQLDataType.TINYINTUNSIGNED)), this, "Versioned if it is true");

    /**
     * The column <code>data.data_storage.IS_AUDITED</code>. Audited if it is
     * true
     */
    public final TableField<DataStorageRecord, UByte> IS_AUDITED = createField(DSL.name("IS_AUDITED"), SQLDataType.TINYINTUNSIGNED.nullable(false).defaultValue(DSL.inline("0", SQLDataType.TINYINTUNSIGNED)), this, "Audited if it is true");

    /**
     * The column <code>data.data_storage.CREATE_AUTH</code>. Authorization
     * expression for creating a row
     */
    public final TableField<DataStorageRecord, String> CREATE_AUTH = createField(DSL.name("CREATE_AUTH"), SQLDataType.VARCHAR(512), this, "Authorization expression for creating a row");

    /**
     * The column <code>data.data_storage.READ_AUTH</code>. Authorization
     * expression for reading a row
     */
    public final TableField<DataStorageRecord, String> READ_AUTH = createField(DSL.name("READ_AUTH"), SQLDataType.VARCHAR(512), this, "Authorization expression for reading a row");

    /**
     * The column <code>data.data_storage.UPDATE_AUTH</code>. Authorization
     * expression for updating a row
     */
    public final TableField<DataStorageRecord, String> UPDATE_AUTH = createField(DSL.name("UPDATE_AUTH"), SQLDataType.VARCHAR(512), this, "Authorization expression for updating a row");

    /**
     * The column <code>data.data_storage.DELETE_AUTH</code>. Authorization
     * expression for deleting a row
     */
    public final TableField<DataStorageRecord, String> DELETE_AUTH = createField(DSL.name("DELETE_AUTH"), SQLDataType.VARCHAR(512), this, "Authorization expression for deleting a row");

    /**
     * The column <code>data.data_storage.INTERNAL_NAME</code>. Name with the
     * storage created.
     */
    public final TableField<DataStorageRecord, String> INTERNAL_NAME = createField(DSL.name("INTERNAL_NAME"), SQLDataType.CHAR(64).nullable(false), this, "Name with the storage created.");

    /**
     * The column <code>data.data_storage.CREATED_BY</code>. ID of the user who
     * created this row
     */
    public final TableField<DataStorageRecord, ULong> CREATED_BY = createField(DSL.name("CREATED_BY"), SQLDataType.BIGINTUNSIGNED, this, "ID of the user who created this row");

    /**
     * The column <code>data.data_storage.CREATED_AT</code>. Time when this row
     * is created
     */
    public final TableField<DataStorageRecord, LocalDateTime> CREATED_AT = createField(DSL.name("CREATED_AT"), SQLDataType.LOCALDATETIME(0).nullable(false), this, "Time when this row is created");

    /**
     * The column <code>data.data_storage.UPDATED_BY</code>. ID of the user who
     * updated this row
     */
    public final TableField<DataStorageRecord, ULong> UPDATED_BY = createField(DSL.name("UPDATED_BY"), SQLDataType.BIGINTUNSIGNED, this, "ID of the user who updated this row");

    /**
     * The column <code>data.data_storage.UPDATED_AT</code>. Time when this row
     * is updated
     */
    public final TableField<DataStorageRecord, LocalDateTime> UPDATED_AT = createField(DSL.name("UPDATED_AT"), SQLDataType.LOCALDATETIME(0).nullable(false), this, "Time when this row is updated");

    /**
     * The column <code>data.data_storage.STATUS</code>.
     */
    public final TableField<DataStorageRecord, DataStorageStatus> STATUS = createField(DSL.name("STATUS"), SQLDataType.VARCHAR(8).nullable(false).defaultValue(DSL.inline("ACTIVE", SQLDataType.VARCHAR)).asEnumDataType(com.fincity.saas.data.jooq.enums.DataStorageStatus.class), this, "");

    private DataStorage(Name alias, Table<DataStorageRecord> aliased) {
        this(alias, aliased, null);
    }

    private DataStorage(Name alias, Table<DataStorageRecord> aliased, Field<?>[] parameters) {
        super(alias, null, aliased, parameters, DSL.comment(""), TableOptions.table());
    }

    /**
     * Create an aliased <code>data.data_storage</code> table reference
     */
    public DataStorage(String alias) {
        this(DSL.name(alias), DATA_STORAGE);
    }

    /**
     * Create an aliased <code>data.data_storage</code> table reference
     */
    public DataStorage(Name alias) {
        this(alias, DATA_STORAGE);
    }

    /**
     * Create a <code>data.data_storage</code> table reference
     */
    public DataStorage() {
        this(DSL.name("data_storage"), null);
    }

    public <O extends Record> DataStorage(Table<O> child, ForeignKey<O, DataStorageRecord> key) {
        super(child, key, DATA_STORAGE);
    }

    @Override
    public Schema getSchema() {
        return aliased() ? null : Data.DATA;
    }

    @Override
    public Identity<DataStorageRecord, ULong> getIdentity() {
        return (Identity<DataStorageRecord, ULong>) super.getIdentity();
    }

    @Override
    public UniqueKey<DataStorageRecord> getPrimaryKey() {
        return Keys.KEY_DATA_STORAGE_PRIMARY;
    }

    @Override
    public List<UniqueKey<DataStorageRecord>> getUniqueKeys() {
        return Arrays.asList(Keys.KEY_DATA_STORAGE_UK1_DATA_STRG);
    }

    @Override
    public DataStorage as(String alias) {
        return new DataStorage(DSL.name(alias), this);
    }

    @Override
    public DataStorage as(Name alias) {
        return new DataStorage(alias, this);
    }

    @Override
    public DataStorage as(Table<?> alias) {
        return new DataStorage(alias.getQualifiedName(), this);
    }

    /**
     * Rename this table
     */
    @Override
    public DataStorage rename(String name) {
        return new DataStorage(DSL.name(name), null);
    }

    /**
     * Rename this table
     */
    @Override
    public DataStorage rename(Name name) {
        return new DataStorage(name, null);
    }

    /**
     * Rename this table
     */
    @Override
    public DataStorage rename(Table<?> name) {
        return new DataStorage(name.getQualifiedName(), null);
    }

    // -------------------------------------------------------------------------
    // Row17 type methods
    // -------------------------------------------------------------------------

    @Override
    public Row17<ULong, String, String, String, String, UByte, UByte, String, String, String, String, String, ULong, LocalDateTime, ULong, LocalDateTime, DataStorageStatus> fieldsRow() {
        return (Row17) super.fieldsRow();
    }

    /**
     * Convenience mapping calling {@link SelectField#convertFrom(Function)}.
     */
    public <U> SelectField<U> mapping(Function17<? super ULong, ? super String, ? super String, ? super String, ? super String, ? super UByte, ? super UByte, ? super String, ? super String, ? super String, ? super String, ? super String, ? super ULong, ? super LocalDateTime, ? super ULong, ? super LocalDateTime, ? super DataStorageStatus, ? extends U> from) {
        return convertFrom(Records.mapping(from));
    }

    /**
     * Convenience mapping calling {@link SelectField#convertFrom(Class,
     * Function)}.
     */
    public <U> SelectField<U> mapping(Class<U> toType, Function17<? super ULong, ? super String, ? super String, ? super String, ? super String, ? super UByte, ? super UByte, ? super String, ? super String, ? super String, ? super String, ? super String, ? super ULong, ? super LocalDateTime, ? super ULong, ? super LocalDateTime, ? super DataStorageStatus, ? extends U> from) {
        return convertFrom(toType, Records.mapping(from));
    }
}
