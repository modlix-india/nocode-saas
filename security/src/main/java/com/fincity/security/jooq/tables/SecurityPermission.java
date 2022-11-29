/*
 * This file is generated by jOOQ.
 */
package com.fincity.security.jooq.tables;


import com.fincity.security.jooq.Keys;
import com.fincity.security.jooq.Security;
import com.fincity.security.jooq.tables.records.SecurityPermissionRecord;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import org.jooq.Field;
import org.jooq.ForeignKey;
import org.jooq.Function9;
import org.jooq.Identity;
import org.jooq.Name;
import org.jooq.Record;
import org.jooq.Records;
import org.jooq.Row9;
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
public class SecurityPermission extends TableImpl<SecurityPermissionRecord> {

    private static final long serialVersionUID = 1L;

    /**
     * The reference instance of <code>security.security_permission</code>
     */
    public static final SecurityPermission SECURITY_PERMISSION = new SecurityPermission();

    /**
     * The class holding records for this type
     */
    @Override
    public Class<SecurityPermissionRecord> getRecordType() {
        return SecurityPermissionRecord.class;
    }

    /**
     * The column <code>security.security_permission.ID</code>. Primary key
     */
    public final TableField<SecurityPermissionRecord, ULong> ID = createField(DSL.name("ID"), SQLDataType.BIGINTUNSIGNED.nullable(false).identity(true), this, "Primary key");

    /**
     * The column <code>security.security_permission.CLIENT_ID</code>. Client ID
     * for which this permission belongs to
     */
    public final TableField<SecurityPermissionRecord, ULong> CLIENT_ID = createField(DSL.name("CLIENT_ID"), SQLDataType.BIGINTUNSIGNED.nullable(false), this, "Client ID for which this permission belongs to");

    /**
     * The column <code>security.security_permission.NAME</code>. Name of the
     * permission
     */
    public final TableField<SecurityPermissionRecord, String> NAME = createField(DSL.name("NAME"), SQLDataType.VARCHAR(256).nullable(false), this, "Name of the permission");

    /**
     * The column <code>security.security_permission.APP_ID</code>.
     */
    public final TableField<SecurityPermissionRecord, ULong> APP_ID = createField(DSL.name("APP_ID"), SQLDataType.BIGINTUNSIGNED, this, "");

    /**
     * The column <code>security.security_permission.DESCRIPTION</code>.
     * Description of the permission
     */
    public final TableField<SecurityPermissionRecord, String> DESCRIPTION = createField(DSL.name("DESCRIPTION"), SQLDataType.CLOB, this, "Description of the permission");

    /**
     * The column <code>security.security_permission.CREATED_BY</code>. ID of
     * the user who created this row
     */
    public final TableField<SecurityPermissionRecord, ULong> CREATED_BY = createField(DSL.name("CREATED_BY"), SQLDataType.BIGINTUNSIGNED, this, "ID of the user who created this row");

    /**
     * The column <code>security.security_permission.CREATED_AT</code>. Time
     * when this row is created
     */
    public final TableField<SecurityPermissionRecord, LocalDateTime> CREATED_AT = createField(DSL.name("CREATED_AT"), SQLDataType.LOCALDATETIME(0).nullable(false), this, "Time when this row is created");

    /**
     * The column <code>security.security_permission.UPDATED_BY</code>. ID of
     * the user who updated this row
     */
    public final TableField<SecurityPermissionRecord, ULong> UPDATED_BY = createField(DSL.name("UPDATED_BY"), SQLDataType.BIGINTUNSIGNED, this, "ID of the user who updated this row");

    /**
     * The column <code>security.security_permission.UPDATED_AT</code>. Time
     * when this row is updated
     */
    public final TableField<SecurityPermissionRecord, LocalDateTime> UPDATED_AT = createField(DSL.name("UPDATED_AT"), SQLDataType.LOCALDATETIME(0).nullable(false), this, "Time when this row is updated");

    private SecurityPermission(Name alias, Table<SecurityPermissionRecord> aliased) {
        this(alias, aliased, null);
    }

    private SecurityPermission(Name alias, Table<SecurityPermissionRecord> aliased, Field<?>[] parameters) {
        super(alias, null, aliased, parameters, DSL.comment(""), TableOptions.table());
    }

    /**
     * Create an aliased <code>security.security_permission</code> table
     * reference
     */
    public SecurityPermission(String alias) {
        this(DSL.name(alias), SECURITY_PERMISSION);
    }

    /**
     * Create an aliased <code>security.security_permission</code> table
     * reference
     */
    public SecurityPermission(Name alias) {
        this(alias, SECURITY_PERMISSION);
    }

    /**
     * Create a <code>security.security_permission</code> table reference
     */
    public SecurityPermission() {
        this(DSL.name("security_permission"), null);
    }

    public <O extends Record> SecurityPermission(Table<O> child, ForeignKey<O, SecurityPermissionRecord> key) {
        super(child, key, SECURITY_PERMISSION);
    }

    @Override
    public Schema getSchema() {
        return aliased() ? null : Security.SECURITY;
    }

    @Override
    public Identity<SecurityPermissionRecord, ULong> getIdentity() {
        return (Identity<SecurityPermissionRecord, ULong>) super.getIdentity();
    }

    @Override
    public UniqueKey<SecurityPermissionRecord> getPrimaryKey() {
        return Keys.KEY_SECURITY_PERMISSION_PRIMARY;
    }

    @Override
    public List<UniqueKey<SecurityPermissionRecord>> getUniqueKeys() {
        return Arrays.asList(Keys.KEY_SECURITY_PERMISSION_UK1_PERMISSION_NAME);
    }

    @Override
    public List<ForeignKey<SecurityPermissionRecord, ?>> getReferences() {
        return Arrays.asList(Keys.FK1_PERMISSION_CLIENT_ID);
    }

    private transient SecurityClient _securityClient;

    /**
     * Get the implicit join path to the <code>security.security_client</code>
     * table.
     */
    public SecurityClient securityClient() {
        if (_securityClient == null)
            _securityClient = new SecurityClient(this, Keys.FK1_PERMISSION_CLIENT_ID);

        return _securityClient;
    }

    @Override
    public SecurityPermission as(String alias) {
        return new SecurityPermission(DSL.name(alias), this);
    }

    @Override
    public SecurityPermission as(Name alias) {
        return new SecurityPermission(alias, this);
    }

    @Override
    public SecurityPermission as(Table<?> alias) {
        return new SecurityPermission(alias.getQualifiedName(), this);
    }

    /**
     * Rename this table
     */
    @Override
    public SecurityPermission rename(String name) {
        return new SecurityPermission(DSL.name(name), null);
    }

    /**
     * Rename this table
     */
    @Override
    public SecurityPermission rename(Name name) {
        return new SecurityPermission(name, null);
    }

    /**
     * Rename this table
     */
    @Override
    public SecurityPermission rename(Table<?> name) {
        return new SecurityPermission(name.getQualifiedName(), null);
    }

    // -------------------------------------------------------------------------
    // Row9 type methods
    // -------------------------------------------------------------------------

    @Override
    public Row9<ULong, ULong, String, ULong, String, ULong, LocalDateTime, ULong, LocalDateTime> fieldsRow() {
        return (Row9) super.fieldsRow();
    }

    /**
     * Convenience mapping calling {@link SelectField#convertFrom(Function)}.
     */
    public <U> SelectField<U> mapping(Function9<? super ULong, ? super ULong, ? super String, ? super ULong, ? super String, ? super ULong, ? super LocalDateTime, ? super ULong, ? super LocalDateTime, ? extends U> from) {
        return convertFrom(Records.mapping(from));
    }

    /**
     * Convenience mapping calling {@link SelectField#convertFrom(Class,
     * Function)}.
     */
    public <U> SelectField<U> mapping(Class<U> toType, Function9<? super ULong, ? super ULong, ? super String, ? super ULong, ? super String, ? super ULong, ? super LocalDateTime, ? super ULong, ? super LocalDateTime, ? extends U> from) {
        return convertFrom(toType, Records.mapping(from));
    }
}
