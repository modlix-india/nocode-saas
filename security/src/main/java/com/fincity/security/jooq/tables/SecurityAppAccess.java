/*
 * This file is generated by jOOQ.
 */
package com.fincity.security.jooq.tables;


import com.fincity.security.jooq.Indexes;
import com.fincity.security.jooq.Keys;
import com.fincity.security.jooq.Security;
import com.fincity.security.jooq.tables.SecurityApp.SecurityAppPath;
import com.fincity.security.jooq.tables.SecurityClient.SecurityClientPath;
import com.fincity.security.jooq.tables.records.SecurityAppAccessRecord;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.ForeignKey;
import org.jooq.Identity;
import org.jooq.Index;
import org.jooq.InverseForeignKey;
import org.jooq.Name;
import org.jooq.Path;
import org.jooq.PlainSQL;
import org.jooq.QueryPart;
import org.jooq.Record;
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
import org.jooq.types.UByte;
import org.jooq.types.ULong;


/**
 * This class is generated by jOOQ.
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes", "this-escape" })
public class SecurityAppAccess extends TableImpl<SecurityAppAccessRecord> {

    private static final long serialVersionUID = 1L;

    /**
     * The reference instance of <code>security.security_app_access</code>
     */
    public static final SecurityAppAccess SECURITY_APP_ACCESS = new SecurityAppAccess();

    /**
     * The class holding records for this type
     */
    @Override
    public Class<SecurityAppAccessRecord> getRecordType() {
        return SecurityAppAccessRecord.class;
    }

    /**
     * The column <code>security.security_app_access.ID</code>. Primary key
     */
    public final TableField<SecurityAppAccessRecord, ULong> ID = createField(DSL.name("ID"), SQLDataType.BIGINTUNSIGNED.nullable(false).identity(true), this, "Primary key");

    /**
     * The column <code>security.security_app_access.CLIENT_ID</code>. Client ID
     */
    public final TableField<SecurityAppAccessRecord, ULong> CLIENT_ID = createField(DSL.name("CLIENT_ID"), SQLDataType.BIGINTUNSIGNED.nullable(false), this, "Client ID");

    /**
     * The column <code>security.security_app_access.APP_ID</code>. Application
     * ID
     */
    public final TableField<SecurityAppAccessRecord, ULong> APP_ID = createField(DSL.name("APP_ID"), SQLDataType.BIGINTUNSIGNED.nullable(false), this, "Application ID");

    /**
     * The column <code>security.security_app_access.EDIT_ACCESS</code>. Edit
     * access
     */
    public final TableField<SecurityAppAccessRecord, UByte> EDIT_ACCESS = createField(DSL.name("EDIT_ACCESS"), SQLDataType.TINYINTUNSIGNED.nullable(false).defaultValue(DSL.inline("0", SQLDataType.TINYINTUNSIGNED)), this, "Edit access");

    /**
     * The column <code>security.security_app_access.CREATED_BY</code>. ID of
     * the user who created this row
     */
    public final TableField<SecurityAppAccessRecord, ULong> CREATED_BY = createField(DSL.name("CREATED_BY"), SQLDataType.BIGINTUNSIGNED, this, "ID of the user who created this row");

    /**
     * The column <code>security.security_app_access.CREATED_AT</code>. Time
     * when this row is created
     */
    public final TableField<SecurityAppAccessRecord, LocalDateTime> CREATED_AT = createField(DSL.name("CREATED_AT"), SQLDataType.LOCALDATETIME(0).nullable(false).defaultValue(DSL.field(DSL.raw("CURRENT_TIMESTAMP"), SQLDataType.LOCALDATETIME)), this, "Time when this row is created");

    /**
     * The column <code>security.security_app_access.UPDATED_BY</code>. ID of
     * the user who updated this row
     */
    public final TableField<SecurityAppAccessRecord, ULong> UPDATED_BY = createField(DSL.name("UPDATED_BY"), SQLDataType.BIGINTUNSIGNED, this, "ID of the user who updated this row");

    /**
     * The column <code>security.security_app_access.UPDATED_AT</code>. Time
     * when this row is updated
     */
    public final TableField<SecurityAppAccessRecord, LocalDateTime> UPDATED_AT = createField(DSL.name("UPDATED_AT"), SQLDataType.LOCALDATETIME(0).nullable(false).defaultValue(DSL.field(DSL.raw("CURRENT_TIMESTAMP"), SQLDataType.LOCALDATETIME)), this, "Time when this row is updated");

    private SecurityAppAccess(Name alias, Table<SecurityAppAccessRecord> aliased) {
        this(alias, aliased, (Field<?>[]) null, null);
    }

    private SecurityAppAccess(Name alias, Table<SecurityAppAccessRecord> aliased, Field<?>[] parameters, Condition where) {
        super(alias, null, aliased, parameters, DSL.comment(""), TableOptions.table(), where);
    }

    /**
     * Create an aliased <code>security.security_app_access</code> table
     * reference
     */
    public SecurityAppAccess(String alias) {
        this(DSL.name(alias), SECURITY_APP_ACCESS);
    }

    /**
     * Create an aliased <code>security.security_app_access</code> table
     * reference
     */
    public SecurityAppAccess(Name alias) {
        this(alias, SECURITY_APP_ACCESS);
    }

    /**
     * Create a <code>security.security_app_access</code> table reference
     */
    public SecurityAppAccess() {
        this(DSL.name("security_app_access"), null);
    }

    public <O extends Record> SecurityAppAccess(Table<O> path, ForeignKey<O, SecurityAppAccessRecord> childPath, InverseForeignKey<O, SecurityAppAccessRecord> parentPath) {
        super(path, childPath, parentPath, SECURITY_APP_ACCESS);
    }

    /**
     * A subtype implementing {@link Path} for simplified path-based joins.
     */
    public static class SecurityAppAccessPath extends SecurityAppAccess implements Path<SecurityAppAccessRecord> {

        private static final long serialVersionUID = 1L;
        public <O extends Record> SecurityAppAccessPath(Table<O> path, ForeignKey<O, SecurityAppAccessRecord> childPath, InverseForeignKey<O, SecurityAppAccessRecord> parentPath) {
            super(path, childPath, parentPath);
        }
        private SecurityAppAccessPath(Name alias, Table<SecurityAppAccessRecord> aliased) {
            super(alias, aliased);
        }

        @Override
        public SecurityAppAccessPath as(String alias) {
            return new SecurityAppAccessPath(DSL.name(alias), this);
        }

        @Override
        public SecurityAppAccessPath as(Name alias) {
            return new SecurityAppAccessPath(alias, this);
        }

        @Override
        public SecurityAppAccessPath as(Table<?> alias) {
            return new SecurityAppAccessPath(alias.getQualifiedName(), this);
        }
    }

    @Override
    public Schema getSchema() {
        return aliased() ? null : Security.SECURITY;
    }

    @Override
    public List<Index> getIndexes() {
        return Arrays.asList(Indexes.SECURITY_APP_ACCESS_FK1_APP_CLIENT_ID);
    }

    @Override
    public Identity<SecurityAppAccessRecord, ULong> getIdentity() {
        return (Identity<SecurityAppAccessRecord, ULong>) super.getIdentity();
    }

    @Override
    public UniqueKey<SecurityAppAccessRecord> getPrimaryKey() {
        return Keys.KEY_SECURITY_APP_ACCESS_PRIMARY;
    }

    @Override
    public List<UniqueKey<SecurityAppAccessRecord>> getUniqueKeys() {
        return Arrays.asList(Keys.KEY_SECURITY_APP_ACCESS_UK1_APPCLIENT);
    }

    @Override
    public List<ForeignKey<SecurityAppAccessRecord, ?>> getReferences() {
        return Arrays.asList(Keys.FK1_APP_ACCESS_APP_ID, Keys.FK1_APP_ACCESS_CLIENT_ID);
    }

    private transient SecurityAppPath _securityApp;

    /**
     * Get the implicit join path to the <code>security.security_app</code>
     * table.
     */
    public SecurityAppPath securityApp() {
        if (_securityApp == null)
            _securityApp = new SecurityAppPath(this, Keys.FK1_APP_ACCESS_APP_ID, null);

        return _securityApp;
    }

    private transient SecurityClientPath _securityClient;

    /**
     * Get the implicit join path to the <code>security.security_client</code>
     * table.
     */
    public SecurityClientPath securityClient() {
        if (_securityClient == null)
            _securityClient = new SecurityClientPath(this, Keys.FK1_APP_ACCESS_CLIENT_ID, null);

        return _securityClient;
    }

    @Override
    public SecurityAppAccess as(String alias) {
        return new SecurityAppAccess(DSL.name(alias), this);
    }

    @Override
    public SecurityAppAccess as(Name alias) {
        return new SecurityAppAccess(alias, this);
    }

    @Override
    public SecurityAppAccess as(Table<?> alias) {
        return new SecurityAppAccess(alias.getQualifiedName(), this);
    }

    /**
     * Rename this table
     */
    @Override
    public SecurityAppAccess rename(String name) {
        return new SecurityAppAccess(DSL.name(name), null);
    }

    /**
     * Rename this table
     */
    @Override
    public SecurityAppAccess rename(Name name) {
        return new SecurityAppAccess(name, null);
    }

    /**
     * Rename this table
     */
    @Override
    public SecurityAppAccess rename(Table<?> name) {
        return new SecurityAppAccess(name.getQualifiedName(), null);
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public SecurityAppAccess where(Condition condition) {
        return new SecurityAppAccess(getQualifiedName(), aliased() ? this : null, null, condition);
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public SecurityAppAccess where(Collection<? extends Condition> conditions) {
        return where(DSL.and(conditions));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public SecurityAppAccess where(Condition... conditions) {
        return where(DSL.and(conditions));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public SecurityAppAccess where(Field<Boolean> condition) {
        return where(DSL.condition(condition));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    @PlainSQL
    public SecurityAppAccess where(SQL condition) {
        return where(DSL.condition(condition));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    @PlainSQL
    public SecurityAppAccess where(@Stringly.SQL String condition) {
        return where(DSL.condition(condition));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    @PlainSQL
    public SecurityAppAccess where(@Stringly.SQL String condition, Object... binds) {
        return where(DSL.condition(condition, binds));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    @PlainSQL
    public SecurityAppAccess where(@Stringly.SQL String condition, QueryPart... parts) {
        return where(DSL.condition(condition, parts));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public SecurityAppAccess whereExists(Select<?> select) {
        return where(DSL.exists(select));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public SecurityAppAccess whereNotExists(Select<?> select) {
        return where(DSL.notExists(select));
    }
}
