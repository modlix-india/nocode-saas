/*
 * This file is generated by jOOQ.
 */
package com.fincity.security.jooq.tables;


import com.fincity.security.jooq.Keys;
import com.fincity.security.jooq.Security;
import com.fincity.security.jooq.tables.records.SecurityAppRegCustomerAccessRecord;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import org.jooq.Field;
import org.jooq.ForeignKey;
import org.jooq.Function8;
import org.jooq.Identity;
import org.jooq.Name;
import org.jooq.Record;
import org.jooq.Records;
import org.jooq.Row8;
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
public class SecurityAppRegCustomerAccess extends TableImpl<SecurityAppRegCustomerAccessRecord> {

    private static final long serialVersionUID = 1L;

    /**
     * The reference instance of
     * <code>security.security_app_reg_customer_access</code>
     */
    public static final SecurityAppRegCustomerAccess SECURITY_APP_REG_CUSTOMER_ACCESS = new SecurityAppRegCustomerAccess();

    /**
     * The class holding records for this type
     */
    @Override
    public Class<SecurityAppRegCustomerAccessRecord> getRecordType() {
        return SecurityAppRegCustomerAccessRecord.class;
    }

    /**
     * The column <code>security.security_app_reg_customer_access.ID</code>.
     * Primary key
     */
    public final TableField<SecurityAppRegCustomerAccessRecord, ULong> ID = createField(DSL.name("ID"), SQLDataType.BIGINTUNSIGNED.nullable(false).identity(true), this, "Primary key");

    /**
     * The column
     * <code>security.security_app_reg_customer_access.CLIENT_ID</code>. Client
     * ID
     */
    public final TableField<SecurityAppRegCustomerAccessRecord, ULong> CLIENT_ID = createField(DSL.name("CLIENT_ID"), SQLDataType.BIGINTUNSIGNED.nullable(false), this, "Client ID");

    /**
     * The column <code>security.security_app_reg_customer_access.APP_ID</code>.
     * App ID
     */
    public final TableField<SecurityAppRegCustomerAccessRecord, ULong> APP_ID = createField(DSL.name("APP_ID"), SQLDataType.BIGINTUNSIGNED.nullable(false), this, "App ID");

    /**
     * The column
     * <code>security.security_app_reg_customer_access.DEP_APP_ID</code>. App ID
     * of the dependent app
     */
    public final TableField<SecurityAppRegCustomerAccessRecord, ULong> DEP_APP_ID = createField(DSL.name("DEP_APP_ID"), SQLDataType.BIGINTUNSIGNED.nullable(false), this, "App ID of the dependent app");

    /**
     * The column
     * <code>security.security_app_reg_customer_access.CREATED_BY</code>. ID of
     * the user who created this row
     */
    public final TableField<SecurityAppRegCustomerAccessRecord, ULong> CREATED_BY = createField(DSL.name("CREATED_BY"), SQLDataType.BIGINTUNSIGNED, this, "ID of the user who created this row");

    /**
     * The column
     * <code>security.security_app_reg_customer_access.CREATED_AT</code>. Time
     * when this row is created
     */
    public final TableField<SecurityAppRegCustomerAccessRecord, LocalDateTime> CREATED_AT = createField(DSL.name("CREATED_AT"), SQLDataType.LOCALDATETIME(0).nullable(false).defaultValue(DSL.field(DSL.raw("CURRENT_TIMESTAMP"), SQLDataType.LOCALDATETIME)), this, "Time when this row is created");

    /**
     * The column
     * <code>security.security_app_reg_customer_access.UPDATED_BY</code>. ID of
     * the user who updated this row
     */
    public final TableField<SecurityAppRegCustomerAccessRecord, ULong> UPDATED_BY = createField(DSL.name("UPDATED_BY"), SQLDataType.BIGINTUNSIGNED, this, "ID of the user who updated this row");

    /**
     * The column
     * <code>security.security_app_reg_customer_access.UPDATED_AT</code>. Time
     * when this row is updated
     */
    public final TableField<SecurityAppRegCustomerAccessRecord, LocalDateTime> UPDATED_AT = createField(DSL.name("UPDATED_AT"), SQLDataType.LOCALDATETIME(0).nullable(false).defaultValue(DSL.field(DSL.raw("CURRENT_TIMESTAMP"), SQLDataType.LOCALDATETIME)), this, "Time when this row is updated");

    private SecurityAppRegCustomerAccess(Name alias, Table<SecurityAppRegCustomerAccessRecord> aliased) {
        this(alias, aliased, null);
    }

    private SecurityAppRegCustomerAccess(Name alias, Table<SecurityAppRegCustomerAccessRecord> aliased, Field<?>[] parameters) {
        super(alias, null, aliased, parameters, DSL.comment(""), TableOptions.table());
    }

    /**
     * Create an aliased <code>security.security_app_reg_customer_access</code>
     * table reference
     */
    public SecurityAppRegCustomerAccess(String alias) {
        this(DSL.name(alias), SECURITY_APP_REG_CUSTOMER_ACCESS);
    }

    /**
     * Create an aliased <code>security.security_app_reg_customer_access</code>
     * table reference
     */
    public SecurityAppRegCustomerAccess(Name alias) {
        this(alias, SECURITY_APP_REG_CUSTOMER_ACCESS);
    }

    /**
     * Create a <code>security.security_app_reg_customer_access</code> table
     * reference
     */
    public SecurityAppRegCustomerAccess() {
        this(DSL.name("security_app_reg_customer_access"), null);
    }

    public <O extends Record> SecurityAppRegCustomerAccess(Table<O> child, ForeignKey<O, SecurityAppRegCustomerAccessRecord> key) {
        super(child, key, SECURITY_APP_REG_CUSTOMER_ACCESS);
    }

    @Override
    public Schema getSchema() {
        return aliased() ? null : Security.SECURITY;
    }

    @Override
    public Identity<SecurityAppRegCustomerAccessRecord, ULong> getIdentity() {
        return (Identity<SecurityAppRegCustomerAccessRecord, ULong>) super.getIdentity();
    }

    @Override
    public UniqueKey<SecurityAppRegCustomerAccessRecord> getPrimaryKey() {
        return Keys.KEY_SECURITY_APP_REG_CUSTOMER_ACCESS_PRIMARY;
    }

    @Override
    public List<UniqueKey<SecurityAppRegCustomerAccessRecord>> getUniqueKeys() {
        return Arrays.asList(Keys.KEY_SECURITY_APP_REG_CUSTOMER_ACCESS_CLIENT_ID);
    }

    @Override
    public List<ForeignKey<SecurityAppRegCustomerAccessRecord, ?>> getReferences() {
        return Arrays.asList(Keys.FK1_APP_REG_CUST_ACC_CLNT_ID, Keys.FK2_APP_REG_CUST_ACC_APP_ID);
    }

    private transient SecurityClient _securityClient;
    private transient SecurityApp _securityApp;

    /**
     * Get the implicit join path to the <code>security.security_client</code>
     * table.
     */
    public SecurityClient securityClient() {
        if (_securityClient == null)
            _securityClient = new SecurityClient(this, Keys.FK1_APP_REG_CUST_ACC_CLNT_ID);

        return _securityClient;
    }

    /**
     * Get the implicit join path to the <code>security.security_app</code>
     * table.
     */
    public SecurityApp securityApp() {
        if (_securityApp == null)
            _securityApp = new SecurityApp(this, Keys.FK2_APP_REG_CUST_ACC_APP_ID);

        return _securityApp;
    }

    @Override
    public SecurityAppRegCustomerAccess as(String alias) {
        return new SecurityAppRegCustomerAccess(DSL.name(alias), this);
    }

    @Override
    public SecurityAppRegCustomerAccess as(Name alias) {
        return new SecurityAppRegCustomerAccess(alias, this);
    }

    @Override
    public SecurityAppRegCustomerAccess as(Table<?> alias) {
        return new SecurityAppRegCustomerAccess(alias.getQualifiedName(), this);
    }

    /**
     * Rename this table
     */
    @Override
    public SecurityAppRegCustomerAccess rename(String name) {
        return new SecurityAppRegCustomerAccess(DSL.name(name), null);
    }

    /**
     * Rename this table
     */
    @Override
    public SecurityAppRegCustomerAccess rename(Name name) {
        return new SecurityAppRegCustomerAccess(name, null);
    }

    /**
     * Rename this table
     */
    @Override
    public SecurityAppRegCustomerAccess rename(Table<?> name) {
        return new SecurityAppRegCustomerAccess(name.getQualifiedName(), null);
    }

    // -------------------------------------------------------------------------
    // Row8 type methods
    // -------------------------------------------------------------------------

    @Override
    public Row8<ULong, ULong, ULong, ULong, ULong, LocalDateTime, ULong, LocalDateTime> fieldsRow() {
        return (Row8) super.fieldsRow();
    }

    /**
     * Convenience mapping calling {@link SelectField#convertFrom(Function)}.
     */
    public <U> SelectField<U> mapping(Function8<? super ULong, ? super ULong, ? super ULong, ? super ULong, ? super ULong, ? super LocalDateTime, ? super ULong, ? super LocalDateTime, ? extends U> from) {
        return convertFrom(Records.mapping(from));
    }

    /**
     * Convenience mapping calling {@link SelectField#convertFrom(Class,
     * Function)}.
     */
    public <U> SelectField<U> mapping(Class<U> toType, Function8<? super ULong, ? super ULong, ? super ULong, ? super ULong, ? super ULong, ? super LocalDateTime, ? super ULong, ? super LocalDateTime, ? extends U> from) {
        return convertFrom(toType, Records.mapping(from));
    }
}
