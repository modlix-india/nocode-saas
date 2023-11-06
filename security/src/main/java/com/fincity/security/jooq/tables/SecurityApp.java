/*
 * This file is generated by jOOQ.
 */
package com.fincity.security.jooq.tables;


import com.fincity.security.jooq.Keys;
import com.fincity.security.jooq.Security;
import com.fincity.security.jooq.enums.SecurityAppAppType;
import com.fincity.security.jooq.tables.records.SecurityAppRecord;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import org.jooq.Field;
import org.jooq.ForeignKey;
import org.jooq.Function10;
import org.jooq.Identity;
import org.jooq.Name;
import org.jooq.Record;
import org.jooq.Records;
import org.jooq.Row10;
import org.jooq.Schema;
import org.jooq.SelectField;
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
@SuppressWarnings({ "all", "unchecked", "rawtypes" })
public class SecurityApp extends TableImpl<SecurityAppRecord> {

    private static final long serialVersionUID = 1L;

    /**
     * The reference instance of <code>security.security_app</code>
     */
    public static final SecurityApp SECURITY_APP = new SecurityApp();

    /**
     * The class holding records for this type
     */
    @Override
    public Class<SecurityAppRecord> getRecordType() {
        return SecurityAppRecord.class;
    }

    /**
     * The column <code>security.security_app.ID</code>. Primary key
     */
    public final TableField<SecurityAppRecord, ULong> ID = createField(DSL.name("ID"), SQLDataType.BIGINTUNSIGNED.nullable(false).identity(true), this, "Primary key");

    /**
     * The column <code>security.security_app.CLIENT_ID</code>. Client ID
     */
    public final TableField<SecurityAppRecord, ULong> CLIENT_ID = createField(DSL.name("CLIENT_ID"), SQLDataType.BIGINTUNSIGNED.nullable(false), this, "Client ID");

    /**
     * The column <code>security.security_app.APP_NAME</code>. Name of the
     * application
     */
    public final TableField<SecurityAppRecord, String> APP_NAME = createField(DSL.name("APP_NAME"), SQLDataType.VARCHAR(512).nullable(false), this, "Name of the application");

    /**
     * The column <code>security.security_app.APP_CODE</code>. Code of the
     * application
     */
    public final TableField<SecurityAppRecord, String> APP_CODE = createField(DSL.name("APP_CODE"), SQLDataType.CHAR(64).nullable(false), this, "Code of the application");

    /**
     * The column <code>security.security_app.APP_TYPE</code>. Application type
     */
    public final TableField<SecurityAppRecord, SecurityAppAppType> APP_TYPE = createField(DSL.name("APP_TYPE"), SQLDataType.VARCHAR(6).nullable(false).defaultValue(DSL.inline("APP", SQLDataType.VARCHAR)).asEnumDataType(com.fincity.security.jooq.enums.SecurityAppAppType.class), this, "Application type");

    /**
     * The column <code>security.security_app.CREATED_BY</code>. ID of the user
     * who created this row
     */
    public final TableField<SecurityAppRecord, ULong> CREATED_BY = createField(DSL.name("CREATED_BY"), SQLDataType.BIGINTUNSIGNED, this, "ID of the user who created this row");

    /**
     * The column <code>security.security_app.CREATED_AT</code>. Time when this
     * row is created
     */
    public final TableField<SecurityAppRecord, LocalDateTime> CREATED_AT = createField(DSL.name("CREATED_AT"), SQLDataType.LOCALDATETIME(0).nullable(false).defaultValue(DSL.field(DSL.raw("CURRENT_TIMESTAMP"), SQLDataType.LOCALDATETIME)), this, "Time when this row is created");

    /**
     * The column <code>security.security_app.UPDATED_BY</code>. ID of the user
     * who updated this row
     */
    public final TableField<SecurityAppRecord, ULong> UPDATED_BY = createField(DSL.name("UPDATED_BY"), SQLDataType.BIGINTUNSIGNED, this, "ID of the user who updated this row");

    /**
     * The column <code>security.security_app.UPDATED_AT</code>. Time when this
     * row is updated
     */
    public final TableField<SecurityAppRecord, LocalDateTime> UPDATED_AT = createField(DSL.name("UPDATED_AT"), SQLDataType.LOCALDATETIME(0).nullable(false).defaultValue(DSL.field(DSL.raw("CURRENT_TIMESTAMP"), SQLDataType.LOCALDATETIME)), this, "Time when this row is updated");

    /**
     * The column <code>security.security_app.IS_TEMPLATE</code>. Is this app or
     * site a template?
     */
    public final TableField<SecurityAppRecord, Byte> IS_TEMPLATE = createField(DSL.name("IS_TEMPLATE"), SQLDataType.TINYINT.nullable(false).defaultValue(DSL.inline("0", SQLDataType.TINYINT)), this, "Is this app or site a template?");

    private SecurityApp(Name alias, Table<SecurityAppRecord> aliased) {
        this(alias, aliased, null);
    }

    private SecurityApp(Name alias, Table<SecurityAppRecord> aliased, Field<?>[] parameters) {
        super(alias, null, aliased, parameters, DSL.comment(""), TableOptions.table());
    }

    /**
     * Create an aliased <code>security.security_app</code> table reference
     */
    public SecurityApp(String alias) {
        this(DSL.name(alias), SECURITY_APP);
    }

    /**
     * Create an aliased <code>security.security_app</code> table reference
     */
    public SecurityApp(Name alias) {
        this(alias, SECURITY_APP);
    }

    /**
     * Create a <code>security.security_app</code> table reference
     */
    public SecurityApp() {
        this(DSL.name("security_app"), null);
    }

    public <O extends Record> SecurityApp(Table<O> child, ForeignKey<O, SecurityAppRecord> key) {
        super(child, key, SECURITY_APP);
    }

    @Override
    public Schema getSchema() {
        return aliased() ? null : Security.SECURITY;
    }

    @Override
    public Identity<SecurityAppRecord, ULong> getIdentity() {
        return (Identity<SecurityAppRecord, ULong>) super.getIdentity();
    }

    @Override
    public UniqueKey<SecurityAppRecord> getPrimaryKey() {
        return Keys.KEY_SECURITY_APP_PRIMARY;
    }

    @Override
    public List<UniqueKey<SecurityAppRecord>> getUniqueKeys() {
        return Arrays.asList(Keys.KEY_SECURITY_APP_UK1_APPCODE);
    }

    @Override
    public List<ForeignKey<SecurityAppRecord, ?>> getReferences() {
        return Arrays.asList(Keys.FK1_APP_CLIENT_ID);
    }

    private transient SecurityClient _securityClient;

    /**
     * Get the implicit join path to the <code>security.security_client</code>
     * table.
     */
    public SecurityClient securityClient() {
        if (_securityClient == null)
            _securityClient = new SecurityClient(this, Keys.FK1_APP_CLIENT_ID);

        return _securityClient;
    }

    @Override
    public SecurityApp as(String alias) {
        return new SecurityApp(DSL.name(alias), this);
    }

    @Override
    public SecurityApp as(Name alias) {
        return new SecurityApp(alias, this);
    }

    @Override
    public SecurityApp as(Table<?> alias) {
        return new SecurityApp(alias.getQualifiedName(), this);
    }

    /**
     * Rename this table
     */
    @Override
    public SecurityApp rename(String name) {
        return new SecurityApp(DSL.name(name), null);
    }

    /**
     * Rename this table
     */
    @Override
    public SecurityApp rename(Name name) {
        return new SecurityApp(name, null);
    }

    /**
     * Rename this table
     */
    @Override
    public SecurityApp rename(Table<?> name) {
        return new SecurityApp(name.getQualifiedName(), null);
    }

    // -------------------------------------------------------------------------
    // Row10 type methods
    // -------------------------------------------------------------------------

    @Override
    public Row10<ULong, ULong, String, String, SecurityAppAppType, ULong, LocalDateTime, ULong, LocalDateTime, Byte> fieldsRow() {
        return (Row10) super.fieldsRow();
    }

    /**
     * Convenience mapping calling {@link SelectField#convertFrom(Function)}.
     */
    public <U> SelectField<U> mapping(Function10<? super ULong, ? super ULong, ? super String, ? super String, ? super SecurityAppAppType, ? super ULong, ? super LocalDateTime, ? super ULong, ? super LocalDateTime, ? super Byte, ? extends U> from) {
        return convertFrom(Records.mapping(from));
    }

    /**
     * Convenience mapping calling {@link SelectField#convertFrom(Class,
     * Function)}.
     */
    public <U> SelectField<U> mapping(Class<U> toType, Function10<? super ULong, ? super ULong, ? super String, ? super String, ? super SecurityAppAppType, ? super ULong, ? super LocalDateTime, ? super ULong, ? super LocalDateTime, ? super Byte, ? extends U> from) {
        return convertFrom(toType, Records.mapping(from));
    }
}
