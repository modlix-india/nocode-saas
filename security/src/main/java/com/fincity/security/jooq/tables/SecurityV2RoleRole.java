/*
 * This file is generated by jOOQ.
 */
package com.fincity.security.jooq.tables;


import com.fincity.security.jooq.Keys;
import com.fincity.security.jooq.Security;
import com.fincity.security.jooq.tables.SecurityV2Role.SecurityV2RolePath;
import com.fincity.security.jooq.tables.records.SecurityV2RoleRoleRecord;

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
public class SecurityV2RoleRole extends TableImpl<SecurityV2RoleRoleRecord> {

    private static final long serialVersionUID = 1L;

    /**
     * The reference instance of <code>security.security_v2_role_role</code>
     */
    public static final SecurityV2RoleRole SECURITY_V2_ROLE_ROLE = new SecurityV2RoleRole();

    /**
     * The class holding records for this type
     */
    @Override
    public Class<SecurityV2RoleRoleRecord> getRecordType() {
        return SecurityV2RoleRoleRecord.class;
    }

    /**
     * The column <code>security.security_v2_role_role.ID</code>. Primary key
     */
    public final TableField<SecurityV2RoleRoleRecord, ULong> ID = createField(DSL.name("ID"), SQLDataType.BIGINTUNSIGNED.nullable(false).identity(true), this, "Primary key");

    /**
     * The column <code>security.security_v2_role_role.ROLE_ID</code>. Role ID
     * in which the sub role is nested
     */
    public final TableField<SecurityV2RoleRoleRecord, ULong> ROLE_ID = createField(DSL.name("ROLE_ID"), SQLDataType.BIGINTUNSIGNED.nullable(false), this, "Role ID in which the sub role is nested");

    /**
     * The column <code>security.security_v2_role_role.SUB_ROLE_ID</code>. Sub
     * Role ID for which this role belongs to
     */
    public final TableField<SecurityV2RoleRoleRecord, ULong> SUB_ROLE_ID = createField(DSL.name("SUB_ROLE_ID"), SQLDataType.BIGINTUNSIGNED.nullable(false), this, "Sub Role ID for which this role belongs to");

    private SecurityV2RoleRole(Name alias, Table<SecurityV2RoleRoleRecord> aliased) {
        this(alias, aliased, (Field<?>[]) null, null);
    }

    private SecurityV2RoleRole(Name alias, Table<SecurityV2RoleRoleRecord> aliased, Field<?>[] parameters, Condition where) {
        super(alias, null, aliased, parameters, DSL.comment(""), TableOptions.table(), where);
    }

    /**
     * Create an aliased <code>security.security_v2_role_role</code> table
     * reference
     */
    public SecurityV2RoleRole(String alias) {
        this(DSL.name(alias), SECURITY_V2_ROLE_ROLE);
    }

    /**
     * Create an aliased <code>security.security_v2_role_role</code> table
     * reference
     */
    public SecurityV2RoleRole(Name alias) {
        this(alias, SECURITY_V2_ROLE_ROLE);
    }

    /**
     * Create a <code>security.security_v2_role_role</code> table reference
     */
    public SecurityV2RoleRole() {
        this(DSL.name("security_v2_role_role"), null);
    }

    public <O extends Record> SecurityV2RoleRole(Table<O> path, ForeignKey<O, SecurityV2RoleRoleRecord> childPath, InverseForeignKey<O, SecurityV2RoleRoleRecord> parentPath) {
        super(path, childPath, parentPath, SECURITY_V2_ROLE_ROLE);
    }

    /**
     * A subtype implementing {@link Path} for simplified path-based joins.
     */
    public static class SecurityV2RoleRolePath extends SecurityV2RoleRole implements Path<SecurityV2RoleRoleRecord> {

        private static final long serialVersionUID = 1L;
        public <O extends Record> SecurityV2RoleRolePath(Table<O> path, ForeignKey<O, SecurityV2RoleRoleRecord> childPath, InverseForeignKey<O, SecurityV2RoleRoleRecord> parentPath) {
            super(path, childPath, parentPath);
        }
        private SecurityV2RoleRolePath(Name alias, Table<SecurityV2RoleRoleRecord> aliased) {
            super(alias, aliased);
        }

        @Override
        public SecurityV2RoleRolePath as(String alias) {
            return new SecurityV2RoleRolePath(DSL.name(alias), this);
        }

        @Override
        public SecurityV2RoleRolePath as(Name alias) {
            return new SecurityV2RoleRolePath(alias, this);
        }

        @Override
        public SecurityV2RoleRolePath as(Table<?> alias) {
            return new SecurityV2RoleRolePath(alias.getQualifiedName(), this);
        }
    }

    @Override
    public Schema getSchema() {
        return aliased() ? null : Security.SECURITY;
    }

    @Override
    public Identity<SecurityV2RoleRoleRecord, ULong> getIdentity() {
        return (Identity<SecurityV2RoleRoleRecord, ULong>) super.getIdentity();
    }

    @Override
    public UniqueKey<SecurityV2RoleRoleRecord> getPrimaryKey() {
        return Keys.KEY_SECURITY_V2_ROLE_ROLE_PRIMARY;
    }

    @Override
    public List<UniqueKey<SecurityV2RoleRoleRecord>> getUniqueKeys() {
        return Arrays.asList(Keys.KEY_SECURITY_V2_ROLE_ROLE_UK1_ROLE_ROLE_ROLE_ID_SUB_ROLE_ID);
    }

    @Override
    public List<ForeignKey<SecurityV2RoleRoleRecord, ?>> getReferences() {
        return Arrays.asList(Keys.FK1_ROLE_ROLE_ROLE_ID, Keys.FK2_ROLE_ROLE_SUB_ROLE_ID);
    }

    private transient SecurityV2RolePath _fk1RoleRoleRoleId;

    /**
     * Get the implicit join path to the <code>security.security_v2_role</code>
     * table, via the <code>FK1_ROLE_ROLE_ROLE_ID</code> key.
     */
    public SecurityV2RolePath fk1RoleRoleRoleId() {
        if (_fk1RoleRoleRoleId == null)
            _fk1RoleRoleRoleId = new SecurityV2RolePath(this, Keys.FK1_ROLE_ROLE_ROLE_ID, null);

        return _fk1RoleRoleRoleId;
    }

    private transient SecurityV2RolePath _fk2RoleRoleSubRoleId;

    /**
     * Get the implicit join path to the <code>security.security_v2_role</code>
     * table, via the <code>FK2_ROLE_ROLE_SUB_ROLE_ID</code> key.
     */
    public SecurityV2RolePath fk2RoleRoleSubRoleId() {
        if (_fk2RoleRoleSubRoleId == null)
            _fk2RoleRoleSubRoleId = new SecurityV2RolePath(this, Keys.FK2_ROLE_ROLE_SUB_ROLE_ID, null);

        return _fk2RoleRoleSubRoleId;
    }

    @Override
    public SecurityV2RoleRole as(String alias) {
        return new SecurityV2RoleRole(DSL.name(alias), this);
    }

    @Override
    public SecurityV2RoleRole as(Name alias) {
        return new SecurityV2RoleRole(alias, this);
    }

    @Override
    public SecurityV2RoleRole as(Table<?> alias) {
        return new SecurityV2RoleRole(alias.getQualifiedName(), this);
    }

    /**
     * Rename this table
     */
    @Override
    public SecurityV2RoleRole rename(String name) {
        return new SecurityV2RoleRole(DSL.name(name), null);
    }

    /**
     * Rename this table
     */
    @Override
    public SecurityV2RoleRole rename(Name name) {
        return new SecurityV2RoleRole(name, null);
    }

    /**
     * Rename this table
     */
    @Override
    public SecurityV2RoleRole rename(Table<?> name) {
        return new SecurityV2RoleRole(name.getQualifiedName(), null);
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public SecurityV2RoleRole where(Condition condition) {
        return new SecurityV2RoleRole(getQualifiedName(), aliased() ? this : null, null, condition);
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public SecurityV2RoleRole where(Collection<? extends Condition> conditions) {
        return where(DSL.and(conditions));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public SecurityV2RoleRole where(Condition... conditions) {
        return where(DSL.and(conditions));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public SecurityV2RoleRole where(Field<Boolean> condition) {
        return where(DSL.condition(condition));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    @PlainSQL
    public SecurityV2RoleRole where(SQL condition) {
        return where(DSL.condition(condition));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    @PlainSQL
    public SecurityV2RoleRole where(@Stringly.SQL String condition) {
        return where(DSL.condition(condition));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    @PlainSQL
    public SecurityV2RoleRole where(@Stringly.SQL String condition, Object... binds) {
        return where(DSL.condition(condition, binds));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    @PlainSQL
    public SecurityV2RoleRole where(@Stringly.SQL String condition, QueryPart... parts) {
        return where(DSL.condition(condition, parts));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public SecurityV2RoleRole whereExists(Select<?> select) {
        return where(DSL.exists(select));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public SecurityV2RoleRole whereNotExists(Select<?> select) {
        return where(DSL.notExists(select));
    }
}
