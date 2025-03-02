/*
 * This file is generated by jOOQ.
 */
package com.fincity.security.jooq.tables;


import com.fincity.security.jooq.Keys;
import com.fincity.security.jooq.Security;
import com.fincity.security.jooq.tables.SecurityApp.SecurityAppPath;
import com.fincity.security.jooq.tables.SecurityClient.SecurityClientPath;
import com.fincity.security.jooq.tables.SecurityClientProfile.SecurityClientProfilePath;
import com.fincity.security.jooq.tables.SecurityProfile.SecurityProfilePath;
import com.fincity.security.jooq.tables.SecurityProfileRole.SecurityProfileRolePath;
import com.fincity.security.jooq.tables.SecurityProfileUser.SecurityProfileUserPath;
import com.fincity.security.jooq.tables.SecurityUser.SecurityUserPath;
import com.fincity.security.jooq.tables.SecurityV2Role.SecurityV2RolePath;
import com.fincity.security.jooq.tables.records.SecurityProfileRecord;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.ForeignKey;
import org.jooq.Identity;
import org.jooq.InverseForeignKey;
import org.jooq.JSON;
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
public class SecurityProfile extends TableImpl<SecurityProfileRecord> {

    private static final long serialVersionUID = 1L;

    /**
     * The reference instance of <code>security.security_profile</code>
     */
    public static final SecurityProfile SECURITY_PROFILE = new SecurityProfile();

    /**
     * The class holding records for this type
     */
    @Override
    public Class<SecurityProfileRecord> getRecordType() {
        return SecurityProfileRecord.class;
    }

    /**
     * The column <code>security.security_profile.ID</code>. Primary key
     */
    public final TableField<SecurityProfileRecord, ULong> ID = createField(DSL.name("ID"), SQLDataType.BIGINTUNSIGNED.nullable(false).identity(true), this, "Primary key");

    /**
     * The column <code>security.security_profile.CLIENT_ID</code>. Client ID
     * for which this profile belongs to
     */
    public final TableField<SecurityProfileRecord, ULong> CLIENT_ID = createField(DSL.name("CLIENT_ID"), SQLDataType.BIGINTUNSIGNED.nullable(false), this, "Client ID for which this profile belongs to");

    /**
     * The column <code>security.security_profile.NAME</code>. Name of the
     * profile
     */
    public final TableField<SecurityProfileRecord, String> NAME = createField(DSL.name("NAME"), SQLDataType.VARCHAR(256).nullable(false), this, "Name of the profile");

    /**
     * The column <code>security.security_profile.APP_ID</code>.
     */
    public final TableField<SecurityProfileRecord, ULong> APP_ID = createField(DSL.name("APP_ID"), SQLDataType.BIGINTUNSIGNED, this, "");

    /**
     * The column <code>security.security_profile.DESCRIPTION</code>.
     * Description of the profile
     */
    public final TableField<SecurityProfileRecord, String> DESCRIPTION = createField(DSL.name("DESCRIPTION"), SQLDataType.CLOB, this, "Description of the profile");

    /**
     * The column <code>security.security_profile.ARRANGEMENT</code>.
     * Arrangement of the profile
     */
    public final TableField<SecurityProfileRecord, JSON> ARRANGEMENT = createField(DSL.name("ARRANGEMENT"), SQLDataType.JSON, this, "Arrangement of the profile");

    /**
     * The column <code>security.security_profile.PARENT_PROFILE_ID</code>.
     * Parent profile from which this profile is derived
     */
    public final TableField<SecurityProfileRecord, ULong> PARENT_PROFILE_ID = createField(DSL.name("PARENT_PROFILE_ID"), SQLDataType.BIGINTUNSIGNED, this, "Parent profile from which this profile is derived");

    /**
     * The column <code>security.security_profile.CREATED_BY</code>. ID of the
     * user who created this row
     */
    public final TableField<SecurityProfileRecord, ULong> CREATED_BY = createField(DSL.name("CREATED_BY"), SQLDataType.BIGINTUNSIGNED, this, "ID of the user who created this row");

    /**
     * The column <code>security.security_profile.CREATED_AT</code>. Time when
     * this row is created
     */
    public final TableField<SecurityProfileRecord, LocalDateTime> CREATED_AT = createField(DSL.name("CREATED_AT"), SQLDataType.LOCALDATETIME(0).nullable(false).defaultValue(DSL.field(DSL.raw("CURRENT_TIMESTAMP"), SQLDataType.LOCALDATETIME)), this, "Time when this row is created");

    /**
     * The column <code>security.security_profile.UPDATED_BY</code>. ID of the
     * user who updated this row
     */
    public final TableField<SecurityProfileRecord, ULong> UPDATED_BY = createField(DSL.name("UPDATED_BY"), SQLDataType.BIGINTUNSIGNED, this, "ID of the user who updated this row");

    /**
     * The column <code>security.security_profile.UPDATED_AT</code>. Time when
     * this row is updated
     */
    public final TableField<SecurityProfileRecord, LocalDateTime> UPDATED_AT = createField(DSL.name("UPDATED_AT"), SQLDataType.LOCALDATETIME(0).nullable(false).defaultValue(DSL.field(DSL.raw("CURRENT_TIMESTAMP"), SQLDataType.LOCALDATETIME)), this, "Time when this row is updated");

    private SecurityProfile(Name alias, Table<SecurityProfileRecord> aliased) {
        this(alias, aliased, (Field<?>[]) null, null);
    }

    private SecurityProfile(Name alias, Table<SecurityProfileRecord> aliased, Field<?>[] parameters, Condition where) {
        super(alias, null, aliased, parameters, DSL.comment(""), TableOptions.table(), where);
    }

    /**
     * Create an aliased <code>security.security_profile</code> table reference
     */
    public SecurityProfile(String alias) {
        this(DSL.name(alias), SECURITY_PROFILE);
    }

    /**
     * Create an aliased <code>security.security_profile</code> table reference
     */
    public SecurityProfile(Name alias) {
        this(alias, SECURITY_PROFILE);
    }

    /**
     * Create a <code>security.security_profile</code> table reference
     */
    public SecurityProfile() {
        this(DSL.name("security_profile"), null);
    }

    public <O extends Record> SecurityProfile(Table<O> path, ForeignKey<O, SecurityProfileRecord> childPath, InverseForeignKey<O, SecurityProfileRecord> parentPath) {
        super(path, childPath, parentPath, SECURITY_PROFILE);
    }

    /**
     * A subtype implementing {@link Path} for simplified path-based joins.
     */
    public static class SecurityProfilePath extends SecurityProfile implements Path<SecurityProfileRecord> {

        private static final long serialVersionUID = 1L;
        public <O extends Record> SecurityProfilePath(Table<O> path, ForeignKey<O, SecurityProfileRecord> childPath, InverseForeignKey<O, SecurityProfileRecord> parentPath) {
            super(path, childPath, parentPath);
        }
        private SecurityProfilePath(Name alias, Table<SecurityProfileRecord> aliased) {
            super(alias, aliased);
        }

        @Override
        public SecurityProfilePath as(String alias) {
            return new SecurityProfilePath(DSL.name(alias), this);
        }

        @Override
        public SecurityProfilePath as(Name alias) {
            return new SecurityProfilePath(alias, this);
        }

        @Override
        public SecurityProfilePath as(Table<?> alias) {
            return new SecurityProfilePath(alias.getQualifiedName(), this);
        }
    }

    @Override
    public Schema getSchema() {
        return aliased() ? null : Security.SECURITY;
    }

    @Override
    public Identity<SecurityProfileRecord, ULong> getIdentity() {
        return (Identity<SecurityProfileRecord, ULong>) super.getIdentity();
    }

    @Override
    public UniqueKey<SecurityProfileRecord> getPrimaryKey() {
        return Keys.KEY_SECURITY_PROFILE_PRIMARY;
    }

    @Override
    public List<UniqueKey<SecurityProfileRecord>> getUniqueKeys() {
        return Arrays.asList(Keys.KEY_SECURITY_PROFILE_UK1_PROFILE_NAME_APP_ID);
    }

    @Override
    public List<ForeignKey<SecurityProfileRecord, ?>> getReferences() {
        return Arrays.asList(Keys.FK1_PROFILE_CLIENT_ID, Keys.FK2_PROFILE_APP_ID, Keys.FK3_PROFILE_PARENT_PROFILE_ID);
    }

    private transient SecurityClientPath _securityClient;

    /**
     * Get the implicit join path to the <code>security.security_client</code>
     * table.
     */
    public SecurityClientPath securityClient() {
        if (_securityClient == null)
            _securityClient = new SecurityClientPath(this, Keys.FK1_PROFILE_CLIENT_ID, null);

        return _securityClient;
    }

    private transient SecurityAppPath _securityApp;

    /**
     * Get the implicit join path to the <code>security.security_app</code>
     * table.
     */
    public SecurityAppPath securityApp() {
        if (_securityApp == null)
            _securityApp = new SecurityAppPath(this, Keys.FK2_PROFILE_APP_ID, null);

        return _securityApp;
    }

    private transient SecurityProfilePath _securityProfile;

    /**
     * Get the implicit join path to the <code>security.security_profile</code>
     * table.
     */
    public SecurityProfilePath securityProfile() {
        if (_securityProfile == null)
            _securityProfile = new SecurityProfilePath(this, Keys.FK3_PROFILE_PARENT_PROFILE_ID, null);

        return _securityProfile;
    }

    private transient SecurityProfileRolePath _securityProfileRole;

    /**
     * Get the implicit to-many join path to the
     * <code>security.security_profile_role</code> table
     */
    public SecurityProfileRolePath securityProfileRole() {
        if (_securityProfileRole == null)
            _securityProfileRole = new SecurityProfileRolePath(this, null, Keys.FK1_PROFILE_ROLE_PROFILE_ID.getInverseKey());

        return _securityProfileRole;
    }

    private transient SecurityProfileUserPath _securityProfileUser;

    /**
     * Get the implicit to-many join path to the
     * <code>security.security_profile_user</code> table
     */
    public SecurityProfileUserPath securityProfileUser() {
        if (_securityProfileUser == null)
            _securityProfileUser = new SecurityProfileUserPath(this, null, Keys.FK1_PROFILE_USER_PROFILE_ID.getInverseKey());

        return _securityProfileUser;
    }

    private transient SecurityClientProfilePath _securityClientProfile;

    /**
     * Get the implicit to-many join path to the
     * <code>security.security_client_profile</code> table
     */
    public SecurityClientProfilePath securityClientProfile() {
        if (_securityClientProfile == null)
            _securityClientProfile = new SecurityClientProfilePath(this, null, Keys.FK2_CLIENT_PROFILE_PROFILE_ID.getInverseKey());

        return _securityClientProfile;
    }

    /**
     * Get the implicit many-to-many join path to the
     * <code>security.security_v2_role</code> table
     */
    public SecurityV2RolePath securityV2Role() {
        return securityProfileRole().securityV2Role();
    }

    /**
     * Get the implicit many-to-many join path to the
     * <code>security.security_user</code> table
     */
    public SecurityUserPath securityUser() {
        return securityProfileUser().securityUser();
    }

    @Override
    public SecurityProfile as(String alias) {
        return new SecurityProfile(DSL.name(alias), this);
    }

    @Override
    public SecurityProfile as(Name alias) {
        return new SecurityProfile(alias, this);
    }

    @Override
    public SecurityProfile as(Table<?> alias) {
        return new SecurityProfile(alias.getQualifiedName(), this);
    }

    /**
     * Rename this table
     */
    @Override
    public SecurityProfile rename(String name) {
        return new SecurityProfile(DSL.name(name), null);
    }

    /**
     * Rename this table
     */
    @Override
    public SecurityProfile rename(Name name) {
        return new SecurityProfile(name, null);
    }

    /**
     * Rename this table
     */
    @Override
    public SecurityProfile rename(Table<?> name) {
        return new SecurityProfile(name.getQualifiedName(), null);
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public SecurityProfile where(Condition condition) {
        return new SecurityProfile(getQualifiedName(), aliased() ? this : null, null, condition);
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public SecurityProfile where(Collection<? extends Condition> conditions) {
        return where(DSL.and(conditions));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public SecurityProfile where(Condition... conditions) {
        return where(DSL.and(conditions));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public SecurityProfile where(Field<Boolean> condition) {
        return where(DSL.condition(condition));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    @PlainSQL
    public SecurityProfile where(SQL condition) {
        return where(DSL.condition(condition));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    @PlainSQL
    public SecurityProfile where(@Stringly.SQL String condition) {
        return where(DSL.condition(condition));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    @PlainSQL
    public SecurityProfile where(@Stringly.SQL String condition, Object... binds) {
        return where(DSL.condition(condition, binds));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    @PlainSQL
    public SecurityProfile where(@Stringly.SQL String condition, QueryPart... parts) {
        return where(DSL.condition(condition, parts));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public SecurityProfile whereExists(Select<?> select) {
        return where(DSL.exists(select));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public SecurityProfile whereNotExists(Select<?> select) {
        return where(DSL.notExists(select));
    }
}
