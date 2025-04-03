/*
 * This file is generated by jOOQ.
 */
package com.fincity.security.jooq.tables;


import com.fincity.security.jooq.Keys;
import com.fincity.security.jooq.Security;
import com.fincity.security.jooq.enums.SecurityAppRegUserRoleV2Level;
import com.fincity.security.jooq.tables.SecurityClient.SecurityClientPath;
import com.fincity.security.jooq.tables.SecurityV2Role.SecurityV2RolePath;
import com.fincity.security.jooq.tables.records.SecurityAppRegUserRoleV2Record;

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
public class SecurityAppRegUserRoleV2 extends TableImpl<SecurityAppRegUserRoleV2Record> {

    private static final long serialVersionUID = 1L;

    /**
     * The reference instance of
     * <code>security.security_app_reg_user_role_v2</code>
     */
    public static final SecurityAppRegUserRoleV2 SECURITY_APP_REG_USER_ROLE_V2 = new SecurityAppRegUserRoleV2();

    /**
     * The class holding records for this type
     */
    @Override
    public Class<SecurityAppRegUserRoleV2Record> getRecordType() {
        return SecurityAppRegUserRoleV2Record.class;
    }

    /**
     * The column <code>security.security_app_reg_user_role_v2.ID</code>.
     * Primary key
     */
    public final TableField<SecurityAppRegUserRoleV2Record, ULong> ID = createField(DSL.name("ID"), SQLDataType.BIGINTUNSIGNED.nullable(false).identity(true), this, "Primary key");

    /**
     * The column <code>security.security_app_reg_user_role_v2.CLIENT_ID</code>.
     * Client ID
     */
    public final TableField<SecurityAppRegUserRoleV2Record, ULong> CLIENT_ID = createField(DSL.name("CLIENT_ID"), SQLDataType.BIGINTUNSIGNED.nullable(false), this, "Client ID");

    /**
     * The column
     * <code>security.security_app_reg_user_role_v2.CLIENT_TYPE</code>. Client
     * type
     */
    public final TableField<SecurityAppRegUserRoleV2Record, String> CLIENT_TYPE = createField(DSL.name("CLIENT_TYPE"), SQLDataType.CHAR(4).nullable(false).defaultValue(DSL.inline("BUS", SQLDataType.CHAR)), this, "Client type");

    /**
     * The column <code>security.security_app_reg_user_role_v2.APP_ID</code>.
     * App ID
     */
    public final TableField<SecurityAppRegUserRoleV2Record, ULong> APP_ID = createField(DSL.name("APP_ID"), SQLDataType.BIGINTUNSIGNED.nullable(false), this, "App ID");

    /**
     * The column <code>security.security_app_reg_user_role_v2.LEVEL</code>.
     * Access level
     */
    public final TableField<SecurityAppRegUserRoleV2Record, SecurityAppRegUserRoleV2Level> LEVEL = createField(DSL.name("LEVEL"), SQLDataType.VARCHAR(8).nullable(false).defaultValue(DSL.inline("CLIENT", SQLDataType.VARCHAR)).asEnumDataType(SecurityAppRegUserRoleV2Level.class), this, "Access level");

    /**
     * The column
     * <code>security.security_app_reg_user_role_v2.BUSINESS_TYPE</code>.
     * Business type
     */
    public final TableField<SecurityAppRegUserRoleV2Record, String> BUSINESS_TYPE = createField(DSL.name("BUSINESS_TYPE"), SQLDataType.CHAR(10).nullable(false).defaultValue(DSL.inline("COMMON", SQLDataType.CHAR)), this, "Business type");

    /**
     * The column <code>security.security_app_reg_user_role_v2.ROLE_ID</code>.
     * Role ID
     */
    public final TableField<SecurityAppRegUserRoleV2Record, ULong> ROLE_ID = createField(DSL.name("ROLE_ID"), SQLDataType.BIGINTUNSIGNED.nullable(false), this, "Role ID");

    /**
     * The column
     * <code>security.security_app_reg_user_role_v2.CREATED_BY</code>. ID of the
     * user who created this row
     */
    public final TableField<SecurityAppRegUserRoleV2Record, ULong> CREATED_BY = createField(DSL.name("CREATED_BY"), SQLDataType.BIGINTUNSIGNED, this, "ID of the user who created this row");

    /**
     * The column
     * <code>security.security_app_reg_user_role_v2.CREATED_AT</code>. Time when
     * this row is created
     */
    public final TableField<SecurityAppRegUserRoleV2Record, LocalDateTime> CREATED_AT = createField(DSL.name("CREATED_AT"), SQLDataType.LOCALDATETIME(0).nullable(false).defaultValue(DSL.field(DSL.raw("CURRENT_TIMESTAMP"), SQLDataType.LOCALDATETIME)), this, "Time when this row is created");

    /**
     * The column
     * <code>security.security_app_reg_user_role_v2.UPDATED_BY</code>. ID of the
     * user who updated this row
     */
    public final TableField<SecurityAppRegUserRoleV2Record, ULong> UPDATED_BY = createField(DSL.name("UPDATED_BY"), SQLDataType.BIGINTUNSIGNED, this, "ID of the user who updated this row");

    /**
     * The column
     * <code>security.security_app_reg_user_role_v2.UPDATED_AT</code>. Time when
     * this row is updated
     */
    public final TableField<SecurityAppRegUserRoleV2Record, LocalDateTime> UPDATED_AT = createField(DSL.name("UPDATED_AT"), SQLDataType.LOCALDATETIME(0).nullable(false).defaultValue(DSL.field(DSL.raw("CURRENT_TIMESTAMP"), SQLDataType.LOCALDATETIME)), this, "Time when this row is updated");

    private SecurityAppRegUserRoleV2(Name alias, Table<SecurityAppRegUserRoleV2Record> aliased) {
        this(alias, aliased, (Field<?>[]) null, null);
    }

    private SecurityAppRegUserRoleV2(Name alias, Table<SecurityAppRegUserRoleV2Record> aliased, Field<?>[] parameters, Condition where) {
        super(alias, null, aliased, parameters, DSL.comment(""), TableOptions.table(), where);
    }

    /**
     * Create an aliased <code>security.security_app_reg_user_role_v2</code>
     * table reference
     */
    public SecurityAppRegUserRoleV2(String alias) {
        this(DSL.name(alias), SECURITY_APP_REG_USER_ROLE_V2);
    }

    /**
     * Create an aliased <code>security.security_app_reg_user_role_v2</code>
     * table reference
     */
    public SecurityAppRegUserRoleV2(Name alias) {
        this(alias, SECURITY_APP_REG_USER_ROLE_V2);
    }

    /**
     * Create a <code>security.security_app_reg_user_role_v2</code> table
     * reference
     */
    public SecurityAppRegUserRoleV2() {
        this(DSL.name("security_app_reg_user_role_v2"), null);
    }

    public <O extends Record> SecurityAppRegUserRoleV2(Table<O> path, ForeignKey<O, SecurityAppRegUserRoleV2Record> childPath, InverseForeignKey<O, SecurityAppRegUserRoleV2Record> parentPath) {
        super(path, childPath, parentPath, SECURITY_APP_REG_USER_ROLE_V2);
    }

    /**
     * A subtype implementing {@link Path} for simplified path-based joins.
     */
    public static class SecurityAppRegUserRoleV2Path extends SecurityAppRegUserRoleV2 implements Path<SecurityAppRegUserRoleV2Record> {

        private static final long serialVersionUID = 1L;
        public <O extends Record> SecurityAppRegUserRoleV2Path(Table<O> path, ForeignKey<O, SecurityAppRegUserRoleV2Record> childPath, InverseForeignKey<O, SecurityAppRegUserRoleV2Record> parentPath) {
            super(path, childPath, parentPath);
        }
        private SecurityAppRegUserRoleV2Path(Name alias, Table<SecurityAppRegUserRoleV2Record> aliased) {
            super(alias, aliased);
        }

        @Override
        public SecurityAppRegUserRoleV2Path as(String alias) {
            return new SecurityAppRegUserRoleV2Path(DSL.name(alias), this);
        }

        @Override
        public SecurityAppRegUserRoleV2Path as(Name alias) {
            return new SecurityAppRegUserRoleV2Path(alias, this);
        }

        @Override
        public SecurityAppRegUserRoleV2Path as(Table<?> alias) {
            return new SecurityAppRegUserRoleV2Path(alias.getQualifiedName(), this);
        }
    }

    @Override
    public Schema getSchema() {
        return aliased() ? null : Security.SECURITY;
    }

    @Override
    public Identity<SecurityAppRegUserRoleV2Record, ULong> getIdentity() {
        return (Identity<SecurityAppRegUserRoleV2Record, ULong>) super.getIdentity();
    }

    @Override
    public UniqueKey<SecurityAppRegUserRoleV2Record> getPrimaryKey() {
        return Keys.KEY_SECURITY_APP_REG_USER_ROLE_V2_PRIMARY;
    }

    @Override
    public List<ForeignKey<SecurityAppRegUserRoleV2Record, ?>> getReferences() {
        return Arrays.asList(Keys.FK1_APP_REG_USER_ROLE_CLIENT_ID, Keys.FK2_APP_REG_USER_ROLE_ROLE_V2_ID);
    }

    private transient SecurityClientPath _securityClient;

    /**
     * Get the implicit join path to the <code>security.security_client</code>
     * table.
     */
    public SecurityClientPath securityClient() {
        if (_securityClient == null)
            _securityClient = new SecurityClientPath(this, Keys.FK1_APP_REG_USER_ROLE_CLIENT_ID, null);

        return _securityClient;
    }

    private transient SecurityV2RolePath _securityV2Role;

    /**
     * Get the implicit join path to the <code>security.security_v2_role</code>
     * table.
     */
    public SecurityV2RolePath securityV2Role() {
        if (_securityV2Role == null)
            _securityV2Role = new SecurityV2RolePath(this, Keys.FK2_APP_REG_USER_ROLE_ROLE_V2_ID, null);

        return _securityV2Role;
    }

    @Override
    public SecurityAppRegUserRoleV2 as(String alias) {
        return new SecurityAppRegUserRoleV2(DSL.name(alias), this);
    }

    @Override
    public SecurityAppRegUserRoleV2 as(Name alias) {
        return new SecurityAppRegUserRoleV2(alias, this);
    }

    @Override
    public SecurityAppRegUserRoleV2 as(Table<?> alias) {
        return new SecurityAppRegUserRoleV2(alias.getQualifiedName(), this);
    }

    /**
     * Rename this table
     */
    @Override
    public SecurityAppRegUserRoleV2 rename(String name) {
        return new SecurityAppRegUserRoleV2(DSL.name(name), null);
    }

    /**
     * Rename this table
     */
    @Override
    public SecurityAppRegUserRoleV2 rename(Name name) {
        return new SecurityAppRegUserRoleV2(name, null);
    }

    /**
     * Rename this table
     */
    @Override
    public SecurityAppRegUserRoleV2 rename(Table<?> name) {
        return new SecurityAppRegUserRoleV2(name.getQualifiedName(), null);
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public SecurityAppRegUserRoleV2 where(Condition condition) {
        return new SecurityAppRegUserRoleV2(getQualifiedName(), aliased() ? this : null, null, condition);
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public SecurityAppRegUserRoleV2 where(Collection<? extends Condition> conditions) {
        return where(DSL.and(conditions));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public SecurityAppRegUserRoleV2 where(Condition... conditions) {
        return where(DSL.and(conditions));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public SecurityAppRegUserRoleV2 where(Field<Boolean> condition) {
        return where(DSL.condition(condition));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    @PlainSQL
    public SecurityAppRegUserRoleV2 where(SQL condition) {
        return where(DSL.condition(condition));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    @PlainSQL
    public SecurityAppRegUserRoleV2 where(@Stringly.SQL String condition) {
        return where(DSL.condition(condition));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    @PlainSQL
    public SecurityAppRegUserRoleV2 where(@Stringly.SQL String condition, Object... binds) {
        return where(DSL.condition(condition, binds));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    @PlainSQL
    public SecurityAppRegUserRoleV2 where(@Stringly.SQL String condition, QueryPart... parts) {
        return where(DSL.condition(condition, parts));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public SecurityAppRegUserRoleV2 whereExists(Select<?> select) {
        return where(DSL.exists(select));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public SecurityAppRegUserRoleV2 whereNotExists(Select<?> select) {
        return where(DSL.notExists(select));
    }
}
