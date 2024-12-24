/*
 * This file is generated by jOOQ.
 */
package com.fincity.security.jooq.tables;


import com.fincity.security.jooq.Keys;
import com.fincity.security.jooq.Security;
import com.fincity.security.jooq.tables.SecurityPackage.SecurityPackagePath;
import com.fincity.security.jooq.tables.SecurityRole.SecurityRolePath;
import com.fincity.security.jooq.tables.records.SecurityPackageRoleRecord;

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
public class SecurityPackageRole extends TableImpl<SecurityPackageRoleRecord> {

    private static final long serialVersionUID = 1L;

    /**
     * The reference instance of <code>security.security_package_role</code>
     */
    public static final SecurityPackageRole SECURITY_PACKAGE_ROLE = new SecurityPackageRole();

    /**
     * The class holding records for this type
     */
    @Override
    public Class<SecurityPackageRoleRecord> getRecordType() {
        return SecurityPackageRoleRecord.class;
    }

    /**
     * The column <code>security.security_package_role.ID</code>. Primary key
     */
    public final TableField<SecurityPackageRoleRecord, ULong> ID = createField(DSL.name("ID"), SQLDataType.BIGINTUNSIGNED.nullable(false).identity(true), this, "Primary key");

    /**
     * The column <code>security.security_package_role.PACKAGE_ID</code>.
     * Package ID
     */
    public final TableField<SecurityPackageRoleRecord, ULong> PACKAGE_ID = createField(DSL.name("PACKAGE_ID"), SQLDataType.BIGINTUNSIGNED.nullable(false), this, "Package ID");

    /**
     * The column <code>security.security_package_role.ROLE_ID</code>. Role ID
     */
    public final TableField<SecurityPackageRoleRecord, ULong> ROLE_ID = createField(DSL.name("ROLE_ID"), SQLDataType.BIGINTUNSIGNED.nullable(false), this, "Role ID");

    private SecurityPackageRole(Name alias, Table<SecurityPackageRoleRecord> aliased) {
        this(alias, aliased, (Field<?>[]) null, null);
    }

    private SecurityPackageRole(Name alias, Table<SecurityPackageRoleRecord> aliased, Field<?>[] parameters, Condition where) {
        super(alias, null, aliased, parameters, DSL.comment(""), TableOptions.table(), where);
    }

    /**
     * Create an aliased <code>security.security_package_role</code> table
     * reference
     */
    public SecurityPackageRole(String alias) {
        this(DSL.name(alias), SECURITY_PACKAGE_ROLE);
    }

    /**
     * Create an aliased <code>security.security_package_role</code> table
     * reference
     */
    public SecurityPackageRole(Name alias) {
        this(alias, SECURITY_PACKAGE_ROLE);
    }

    /**
     * Create a <code>security.security_package_role</code> table reference
     */
    public SecurityPackageRole() {
        this(DSL.name("security_package_role"), null);
    }

    public <O extends Record> SecurityPackageRole(Table<O> path, ForeignKey<O, SecurityPackageRoleRecord> childPath, InverseForeignKey<O, SecurityPackageRoleRecord> parentPath) {
        super(path, childPath, parentPath, SECURITY_PACKAGE_ROLE);
    }

    /**
     * A subtype implementing {@link Path} for simplified path-based joins.
     */
    public static class SecurityPackageRolePath extends SecurityPackageRole implements Path<SecurityPackageRoleRecord> {

        private static final long serialVersionUID = 1L;
        public <O extends Record> SecurityPackageRolePath(Table<O> path, ForeignKey<O, SecurityPackageRoleRecord> childPath, InverseForeignKey<O, SecurityPackageRoleRecord> parentPath) {
            super(path, childPath, parentPath);
        }
        private SecurityPackageRolePath(Name alias, Table<SecurityPackageRoleRecord> aliased) {
            super(alias, aliased);
        }

        @Override
        public SecurityPackageRolePath as(String alias) {
            return new SecurityPackageRolePath(DSL.name(alias), this);
        }

        @Override
        public SecurityPackageRolePath as(Name alias) {
            return new SecurityPackageRolePath(alias, this);
        }

        @Override
        public SecurityPackageRolePath as(Table<?> alias) {
            return new SecurityPackageRolePath(alias.getQualifiedName(), this);
        }
    }

    @Override
    public Schema getSchema() {
        return aliased() ? null : Security.SECURITY;
    }

    @Override
    public Identity<SecurityPackageRoleRecord, ULong> getIdentity() {
        return (Identity<SecurityPackageRoleRecord, ULong>) super.getIdentity();
    }

    @Override
    public UniqueKey<SecurityPackageRoleRecord> getPrimaryKey() {
        return Keys.KEY_SECURITY_PACKAGE_ROLE_PRIMARY;
    }

    @Override
    public List<UniqueKey<SecurityPackageRoleRecord>> getUniqueKeys() {
        return Arrays.asList(Keys.KEY_SECURITY_PACKAGE_ROLE_UK1_PACKAGE_ROLE);
    }

    @Override
    public List<ForeignKey<SecurityPackageRoleRecord, ?>> getReferences() {
        return Arrays.asList(Keys.FK1_PACKAGE_ROLE_ROLE_ID, Keys.FK2_PACKAGE_ROLE_PACKAGE_ID);
    }

    private transient SecurityRolePath _securityRole;

    /**
     * Get the implicit join path to the <code>security.security_role</code>
     * table.
     */
    public SecurityRolePath securityRole() {
        if (_securityRole == null)
            _securityRole = new SecurityRolePath(this, Keys.FK1_PACKAGE_ROLE_ROLE_ID, null);

        return _securityRole;
    }

    private transient SecurityPackagePath _securityPackage;

    /**
     * Get the implicit join path to the <code>security.security_package</code>
     * table.
     */
    public SecurityPackagePath securityPackage() {
        if (_securityPackage == null)
            _securityPackage = new SecurityPackagePath(this, Keys.FK2_PACKAGE_ROLE_PACKAGE_ID, null);

        return _securityPackage;
    }

    @Override
    public SecurityPackageRole as(String alias) {
        return new SecurityPackageRole(DSL.name(alias), this);
    }

    @Override
    public SecurityPackageRole as(Name alias) {
        return new SecurityPackageRole(alias, this);
    }

    @Override
    public SecurityPackageRole as(Table<?> alias) {
        return new SecurityPackageRole(alias.getQualifiedName(), this);
    }

    /**
     * Rename this table
     */
    @Override
    public SecurityPackageRole rename(String name) {
        return new SecurityPackageRole(DSL.name(name), null);
    }

    /**
     * Rename this table
     */
    @Override
    public SecurityPackageRole rename(Name name) {
        return new SecurityPackageRole(name, null);
    }

    /**
     * Rename this table
     */
    @Override
    public SecurityPackageRole rename(Table<?> name) {
        return new SecurityPackageRole(name.getQualifiedName(), null);
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public SecurityPackageRole where(Condition condition) {
        return new SecurityPackageRole(getQualifiedName(), aliased() ? this : null, null, condition);
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public SecurityPackageRole where(Collection<? extends Condition> conditions) {
        return where(DSL.and(conditions));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public SecurityPackageRole where(Condition... conditions) {
        return where(DSL.and(conditions));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public SecurityPackageRole where(Field<Boolean> condition) {
        return where(DSL.condition(condition));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    @PlainSQL
    public SecurityPackageRole where(SQL condition) {
        return where(DSL.condition(condition));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    @PlainSQL
    public SecurityPackageRole where(@Stringly.SQL String condition) {
        return where(DSL.condition(condition));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    @PlainSQL
    public SecurityPackageRole where(@Stringly.SQL String condition, Object... binds) {
        return where(DSL.condition(condition, binds));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    @PlainSQL
    public SecurityPackageRole where(@Stringly.SQL String condition, QueryPart... parts) {
        return where(DSL.condition(condition, parts));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public SecurityPackageRole whereExists(Select<?> select) {
        return where(DSL.exists(select));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public SecurityPackageRole whereNotExists(Select<?> select) {
        return where(DSL.notExists(select));
    }
}
