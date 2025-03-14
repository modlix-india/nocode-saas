/*
 * This file is generated by jOOQ.
 */
package com.fincity.security.jooq.tables;


import com.fincity.security.jooq.Keys;
import com.fincity.security.jooq.Security;
import com.fincity.security.jooq.enums.SecurityAppRegUserRoleLevel;
import com.fincity.security.jooq.tables.SecurityApp.SecurityAppPath;
import com.fincity.security.jooq.tables.SecurityClient.SecurityClientPath;
import com.fincity.security.jooq.tables.SecurityClientType.SecurityClientTypePath;
import com.fincity.security.jooq.tables.SecurityRole.SecurityRolePath;
import com.fincity.security.jooq.tables.records.SecurityAppRegUserRoleRecord;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.ForeignKey;
import org.jooq.Identity;
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
import org.jooq.types.ULong;


/**
 * This class is generated by jOOQ.
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes", "this-escape" })
public class SecurityAppRegUserRole extends TableImpl<SecurityAppRegUserRoleRecord> {

    private static final long serialVersionUID = 1L;

    /**
     * The reference instance of
     * <code>security.security_app_reg_user_role</code>
     */
    public static final SecurityAppRegUserRole SECURITY_APP_REG_USER_ROLE = new SecurityAppRegUserRole();

    /**
     * The class holding records for this type
     */
    @Override
    public Class<SecurityAppRegUserRoleRecord> getRecordType() {
        return SecurityAppRegUserRoleRecord.class;
    }

    /**
     * The column <code>security.security_app_reg_user_role.ID</code>. Primary
     * key
     */
    public final TableField<SecurityAppRegUserRoleRecord, ULong> ID = createField(DSL.name("ID"), SQLDataType.BIGINTUNSIGNED.nullable(false).identity(true), this, "Primary key");

    /**
     * The column <code>security.security_app_reg_user_role.CLIENT_ID</code>.
     * Client ID
     */
    public final TableField<SecurityAppRegUserRoleRecord, ULong> CLIENT_ID = createField(DSL.name("CLIENT_ID"), SQLDataType.BIGINTUNSIGNED.nullable(false), this, "Client ID");

    /**
     * The column <code>security.security_app_reg_user_role.CLIENT_TYPE</code>.
     * Client type
     */
    public final TableField<SecurityAppRegUserRoleRecord, String> CLIENT_TYPE = createField(DSL.name("CLIENT_TYPE"), SQLDataType.CHAR(4).nullable(false).defaultValue(DSL.inline("BUS", SQLDataType.CHAR)), this, "Client type");

    /**
     * The column <code>security.security_app_reg_user_role.APP_ID</code>. App
     * ID
     */
    public final TableField<SecurityAppRegUserRoleRecord, ULong> APP_ID = createField(DSL.name("APP_ID"), SQLDataType.BIGINTUNSIGNED.nullable(false), this, "App ID");

    /**
     * The column <code>security.security_app_reg_user_role.ROLE_ID</code>. Role
     * ID
     */
    public final TableField<SecurityAppRegUserRoleRecord, ULong> ROLE_ID = createField(DSL.name("ROLE_ID"), SQLDataType.BIGINTUNSIGNED.nullable(false), this, "Role ID");

    /**
     * The column <code>security.security_app_reg_user_role.LEVEL</code>. Access
     * level
     */
    public final TableField<SecurityAppRegUserRoleRecord, SecurityAppRegUserRoleLevel> LEVEL = createField(DSL.name("LEVEL"), SQLDataType.VARCHAR(8).nullable(false).defaultValue(DSL.inline("CLIENT", SQLDataType.VARCHAR)).asEnumDataType(SecurityAppRegUserRoleLevel.class), this, "Access level");

    /**
     * The column
     * <code>security.security_app_reg_user_role.BUSINESS_TYPE</code>. Business
     * type
     */
    public final TableField<SecurityAppRegUserRoleRecord, String> BUSINESS_TYPE = createField(DSL.name("BUSINESS_TYPE"), SQLDataType.CHAR(10).nullable(false).defaultValue(DSL.inline("COMMON", SQLDataType.CHAR)), this, "Business type");

    /**
     * The column <code>security.security_app_reg_user_role.CREATED_BY</code>.
     * ID of the user who created this row
     */
    public final TableField<SecurityAppRegUserRoleRecord, ULong> CREATED_BY = createField(DSL.name("CREATED_BY"), SQLDataType.BIGINTUNSIGNED, this, "ID of the user who created this row");

    /**
     * The column <code>security.security_app_reg_user_role.CREATED_AT</code>.
     * Time when this row is created
     */
    public final TableField<SecurityAppRegUserRoleRecord, LocalDateTime> CREATED_AT = createField(DSL.name("CREATED_AT"), SQLDataType.LOCALDATETIME(0).nullable(false).defaultValue(DSL.field(DSL.raw("CURRENT_TIMESTAMP"), SQLDataType.LOCALDATETIME)), this, "Time when this row is created");

    private SecurityAppRegUserRole(Name alias, Table<SecurityAppRegUserRoleRecord> aliased) {
        this(alias, aliased, (Field<?>[]) null, null);
    }

    private SecurityAppRegUserRole(Name alias, Table<SecurityAppRegUserRoleRecord> aliased, Field<?>[] parameters, Condition where) {
        super(alias, null, aliased, parameters, DSL.comment(""), TableOptions.table(), where);
    }

    /**
     * Create an aliased <code>security.security_app_reg_user_role</code> table
     * reference
     */
    public SecurityAppRegUserRole(String alias) {
        this(DSL.name(alias), SECURITY_APP_REG_USER_ROLE);
    }

    /**
     * Create an aliased <code>security.security_app_reg_user_role</code> table
     * reference
     */
    public SecurityAppRegUserRole(Name alias) {
        this(alias, SECURITY_APP_REG_USER_ROLE);
    }

    /**
     * Create a <code>security.security_app_reg_user_role</code> table reference
     */
    public SecurityAppRegUserRole() {
        this(DSL.name("security_app_reg_user_role"), null);
    }

    public <O extends Record> SecurityAppRegUserRole(Table<O> path, ForeignKey<O, SecurityAppRegUserRoleRecord> childPath, InverseForeignKey<O, SecurityAppRegUserRoleRecord> parentPath) {
        super(path, childPath, parentPath, SECURITY_APP_REG_USER_ROLE);
    }

    /**
     * A subtype implementing {@link Path} for simplified path-based joins.
     */
    public static class SecurityAppRegUserRolePath extends SecurityAppRegUserRole implements Path<SecurityAppRegUserRoleRecord> {

        private static final long serialVersionUID = 1L;
        public <O extends Record> SecurityAppRegUserRolePath(Table<O> path, ForeignKey<O, SecurityAppRegUserRoleRecord> childPath, InverseForeignKey<O, SecurityAppRegUserRoleRecord> parentPath) {
            super(path, childPath, parentPath);
        }
        private SecurityAppRegUserRolePath(Name alias, Table<SecurityAppRegUserRoleRecord> aliased) {
            super(alias, aliased);
        }

        @Override
        public SecurityAppRegUserRolePath as(String alias) {
            return new SecurityAppRegUserRolePath(DSL.name(alias), this);
        }

        @Override
        public SecurityAppRegUserRolePath as(Name alias) {
            return new SecurityAppRegUserRolePath(alias, this);
        }

        @Override
        public SecurityAppRegUserRolePath as(Table<?> alias) {
            return new SecurityAppRegUserRolePath(alias.getQualifiedName(), this);
        }
    }

    @Override
    public Schema getSchema() {
        return aliased() ? null : Security.SECURITY;
    }

    @Override
    public Identity<SecurityAppRegUserRoleRecord, ULong> getIdentity() {
        return (Identity<SecurityAppRegUserRoleRecord, ULong>) super.getIdentity();
    }

    @Override
    public UniqueKey<SecurityAppRegUserRoleRecord> getPrimaryKey() {
        return Keys.KEY_SECURITY_APP_REG_USER_ROLE_PRIMARY;
    }

    @Override
    public List<UniqueKey<SecurityAppRegUserRoleRecord>> getUniqueKeys() {
        return Arrays.asList(Keys.KEY_SECURITY_APP_REG_USER_ROLE_CLIENT_ID);
    }

    @Override
    public List<ForeignKey<SecurityAppRegUserRoleRecord, ?>> getReferences() {
        return Arrays.asList(Keys.FK1_APP_REG_ROLE_CLNT_ID, Keys.FK2_APP_REG_ROLE_APP_ID, Keys.FK3_APP_REG_ROLE_ROLE_ID, Keys.FK4_APP_REG_ROLE_CLIENT_TYPE);
    }

    private transient SecurityClientPath _securityClient;

    /**
     * Get the implicit join path to the <code>security.security_client</code>
     * table.
     */
    public SecurityClientPath securityClient() {
        if (_securityClient == null)
            _securityClient = new SecurityClientPath(this, Keys.FK1_APP_REG_ROLE_CLNT_ID, null);

        return _securityClient;
    }

    private transient SecurityAppPath _securityApp;

    /**
     * Get the implicit join path to the <code>security.security_app</code>
     * table.
     */
    public SecurityAppPath securityApp() {
        if (_securityApp == null)
            _securityApp = new SecurityAppPath(this, Keys.FK2_APP_REG_ROLE_APP_ID, null);

        return _securityApp;
    }

    private transient SecurityRolePath _securityRole;

    /**
     * Get the implicit join path to the <code>security.security_role</code>
     * table.
     */
    public SecurityRolePath securityRole() {
        if (_securityRole == null)
            _securityRole = new SecurityRolePath(this, Keys.FK3_APP_REG_ROLE_ROLE_ID, null);

        return _securityRole;
    }

    private transient SecurityClientTypePath _securityClientType;

    /**
     * Get the implicit join path to the
     * <code>security.security_client_type</code> table.
     */
    public SecurityClientTypePath securityClientType() {
        if (_securityClientType == null)
            _securityClientType = new SecurityClientTypePath(this, Keys.FK4_APP_REG_ROLE_CLIENT_TYPE, null);

        return _securityClientType;
    }

    @Override
    public SecurityAppRegUserRole as(String alias) {
        return new SecurityAppRegUserRole(DSL.name(alias), this);
    }

    @Override
    public SecurityAppRegUserRole as(Name alias) {
        return new SecurityAppRegUserRole(alias, this);
    }

    @Override
    public SecurityAppRegUserRole as(Table<?> alias) {
        return new SecurityAppRegUserRole(alias.getQualifiedName(), this);
    }

    /**
     * Rename this table
     */
    @Override
    public SecurityAppRegUserRole rename(String name) {
        return new SecurityAppRegUserRole(DSL.name(name), null);
    }

    /**
     * Rename this table
     */
    @Override
    public SecurityAppRegUserRole rename(Name name) {
        return new SecurityAppRegUserRole(name, null);
    }

    /**
     * Rename this table
     */
    @Override
    public SecurityAppRegUserRole rename(Table<?> name) {
        return new SecurityAppRegUserRole(name.getQualifiedName(), null);
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public SecurityAppRegUserRole where(Condition condition) {
        return new SecurityAppRegUserRole(getQualifiedName(), aliased() ? this : null, null, condition);
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public SecurityAppRegUserRole where(Collection<? extends Condition> conditions) {
        return where(DSL.and(conditions));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public SecurityAppRegUserRole where(Condition... conditions) {
        return where(DSL.and(conditions));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public SecurityAppRegUserRole where(Field<Boolean> condition) {
        return where(DSL.condition(condition));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    @PlainSQL
    public SecurityAppRegUserRole where(SQL condition) {
        return where(DSL.condition(condition));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    @PlainSQL
    public SecurityAppRegUserRole where(@Stringly.SQL String condition) {
        return where(DSL.condition(condition));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    @PlainSQL
    public SecurityAppRegUserRole where(@Stringly.SQL String condition, Object... binds) {
        return where(DSL.condition(condition, binds));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    @PlainSQL
    public SecurityAppRegUserRole where(@Stringly.SQL String condition, QueryPart... parts) {
        return where(DSL.condition(condition, parts));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public SecurityAppRegUserRole whereExists(Select<?> select) {
        return where(DSL.exists(select));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public SecurityAppRegUserRole whereNotExists(Select<?> select) {
        return where(DSL.notExists(select));
    }
}
