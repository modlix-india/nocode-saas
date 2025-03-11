/*
 * This file is generated by jOOQ.
 */
package com.fincity.security.jooq.tables;


import com.fincity.security.jooq.Keys;
import com.fincity.security.jooq.Security;
import com.fincity.security.jooq.enums.SecurityAppRegUserProfileLevel;
import com.fincity.security.jooq.tables.SecurityApp.SecurityAppPath;
import com.fincity.security.jooq.tables.SecurityClient.SecurityClientPath;
import com.fincity.security.jooq.tables.SecurityClientType.SecurityClientTypePath;
import com.fincity.security.jooq.tables.SecurityProfile.SecurityProfilePath;
import com.fincity.security.jooq.tables.records.SecurityAppRegUserProfileRecord;

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
public class SecurityAppRegUserProfile extends TableImpl<SecurityAppRegUserProfileRecord> {

    private static final long serialVersionUID = 1L;

    /**
     * The reference instance of
     * <code>security.security_app_reg_user_profile</code>
     */
    public static final SecurityAppRegUserProfile SECURITY_APP_REG_USER_PROFILE = new SecurityAppRegUserProfile();

    /**
     * The class holding records for this type
     */
    @Override
    public Class<SecurityAppRegUserProfileRecord> getRecordType() {
        return SecurityAppRegUserProfileRecord.class;
    }

    /**
     * The column <code>security.security_app_reg_user_profile.ID</code>.
     * Primary key
     */
    public final TableField<SecurityAppRegUserProfileRecord, ULong> ID = createField(DSL.name("ID"), SQLDataType.BIGINTUNSIGNED.nullable(false).identity(true), this, "Primary key");

    /**
     * The column <code>security.security_app_reg_user_profile.CLIENT_ID</code>.
     * Client ID
     */
    public final TableField<SecurityAppRegUserProfileRecord, ULong> CLIENT_ID = createField(DSL.name("CLIENT_ID"), SQLDataType.BIGINTUNSIGNED.nullable(false), this, "Client ID");

    /**
     * The column
     * <code>security.security_app_reg_user_profile.CLIENT_TYPE</code>. Client
     * type
     */
    public final TableField<SecurityAppRegUserProfileRecord, String> CLIENT_TYPE = createField(DSL.name("CLIENT_TYPE"), SQLDataType.CHAR(4).nullable(false).defaultValue(DSL.inline("BUS", SQLDataType.CHAR)), this, "Client type");

    /**
     * The column <code>security.security_app_reg_user_profile.APP_ID</code>.
     * App ID
     */
    public final TableField<SecurityAppRegUserProfileRecord, ULong> APP_ID = createField(DSL.name("APP_ID"), SQLDataType.BIGINTUNSIGNED.nullable(false), this, "App ID");

    /**
     * The column <code>security.security_app_reg_user_profile.LEVEL</code>.
     * Access level
     */
    public final TableField<SecurityAppRegUserProfileRecord, SecurityAppRegUserProfileLevel> LEVEL = createField(DSL.name("LEVEL"), SQLDataType.VARCHAR(8).nullable(false).defaultValue(DSL.inline("CLIENT", SQLDataType.VARCHAR)).asEnumDataType(SecurityAppRegUserProfileLevel.class), this, "Access level");

    /**
     * The column
     * <code>security.security_app_reg_user_profile.BUSINESS_TYPE</code>.
     * Business type
     */
    public final TableField<SecurityAppRegUserProfileRecord, String> BUSINESS_TYPE = createField(DSL.name("BUSINESS_TYPE"), SQLDataType.CHAR(10).nullable(false).defaultValue(DSL.inline("COMMON", SQLDataType.CHAR)), this, "Business type");

    /**
     * The column
     * <code>security.security_app_reg_user_profile.PROFILE_ID</code>. Profile
     * ID
     */
    public final TableField<SecurityAppRegUserProfileRecord, ULong> PROFILE_ID = createField(DSL.name("PROFILE_ID"), SQLDataType.BIGINTUNSIGNED.nullable(false), this, "Profile ID");

    /**
     * The column
     * <code>security.security_app_reg_user_profile.CREATED_BY</code>. ID of the
     * user who created this row
     */
    public final TableField<SecurityAppRegUserProfileRecord, ULong> CREATED_BY = createField(DSL.name("CREATED_BY"), SQLDataType.BIGINTUNSIGNED, this, "ID of the user who created this row");

    /**
     * The column
     * <code>security.security_app_reg_user_profile.CREATED_AT</code>. Time when
     * this row is created
     */
    public final TableField<SecurityAppRegUserProfileRecord, LocalDateTime> CREATED_AT = createField(DSL.name("CREATED_AT"), SQLDataType.LOCALDATETIME(0).nullable(false).defaultValue(DSL.field(DSL.raw("CURRENT_TIMESTAMP"), SQLDataType.LOCALDATETIME)), this, "Time when this row is created");

    /**
     * The column
     * <code>security.security_app_reg_user_profile.UPDATED_BY</code>. ID of the
     * user who updated this row
     */
    public final TableField<SecurityAppRegUserProfileRecord, ULong> UPDATED_BY = createField(DSL.name("UPDATED_BY"), SQLDataType.BIGINTUNSIGNED, this, "ID of the user who updated this row");

    /**
     * The column
     * <code>security.security_app_reg_user_profile.UPDATED_AT</code>. Time when
     * this row is updated
     */
    public final TableField<SecurityAppRegUserProfileRecord, LocalDateTime> UPDATED_AT = createField(DSL.name("UPDATED_AT"), SQLDataType.LOCALDATETIME(0).nullable(false).defaultValue(DSL.field(DSL.raw("CURRENT_TIMESTAMP"), SQLDataType.LOCALDATETIME)), this, "Time when this row is updated");

    private SecurityAppRegUserProfile(Name alias, Table<SecurityAppRegUserProfileRecord> aliased) {
        this(alias, aliased, (Field<?>[]) null, null);
    }

    private SecurityAppRegUserProfile(Name alias, Table<SecurityAppRegUserProfileRecord> aliased, Field<?>[] parameters, Condition where) {
        super(alias, null, aliased, parameters, DSL.comment(""), TableOptions.table(), where);
    }

    /**
     * Create an aliased <code>security.security_app_reg_user_profile</code>
     * table reference
     */
    public SecurityAppRegUserProfile(String alias) {
        this(DSL.name(alias), SECURITY_APP_REG_USER_PROFILE);
    }

    /**
     * Create an aliased <code>security.security_app_reg_user_profile</code>
     * table reference
     */
    public SecurityAppRegUserProfile(Name alias) {
        this(alias, SECURITY_APP_REG_USER_PROFILE);
    }

    /**
     * Create a <code>security.security_app_reg_user_profile</code> table
     * reference
     */
    public SecurityAppRegUserProfile() {
        this(DSL.name("security_app_reg_user_profile"), null);
    }

    public <O extends Record> SecurityAppRegUserProfile(Table<O> path, ForeignKey<O, SecurityAppRegUserProfileRecord> childPath, InverseForeignKey<O, SecurityAppRegUserProfileRecord> parentPath) {
        super(path, childPath, parentPath, SECURITY_APP_REG_USER_PROFILE);
    }

    /**
     * A subtype implementing {@link Path} for simplified path-based joins.
     */
    public static class SecurityAppRegUserProfilePath extends SecurityAppRegUserProfile implements Path<SecurityAppRegUserProfileRecord> {

        private static final long serialVersionUID = 1L;
        public <O extends Record> SecurityAppRegUserProfilePath(Table<O> path, ForeignKey<O, SecurityAppRegUserProfileRecord> childPath, InverseForeignKey<O, SecurityAppRegUserProfileRecord> parentPath) {
            super(path, childPath, parentPath);
        }
        private SecurityAppRegUserProfilePath(Name alias, Table<SecurityAppRegUserProfileRecord> aliased) {
            super(alias, aliased);
        }

        @Override
        public SecurityAppRegUserProfilePath as(String alias) {
            return new SecurityAppRegUserProfilePath(DSL.name(alias), this);
        }

        @Override
        public SecurityAppRegUserProfilePath as(Name alias) {
            return new SecurityAppRegUserProfilePath(alias, this);
        }

        @Override
        public SecurityAppRegUserProfilePath as(Table<?> alias) {
            return new SecurityAppRegUserProfilePath(alias.getQualifiedName(), this);
        }
    }

    @Override
    public Schema getSchema() {
        return aliased() ? null : Security.SECURITY;
    }

    @Override
    public Identity<SecurityAppRegUserProfileRecord, ULong> getIdentity() {
        return (Identity<SecurityAppRegUserProfileRecord, ULong>) super.getIdentity();
    }

    @Override
    public UniqueKey<SecurityAppRegUserProfileRecord> getPrimaryKey() {
        return Keys.KEY_SECURITY_APP_REG_USER_PROFILE_PRIMARY;
    }

    @Override
    public List<UniqueKey<SecurityAppRegUserProfileRecord>> getUniqueKeys() {
        return Arrays.asList(Keys.KEY_SECURITY_APP_REG_USER_PROFILE_CLIENT_ID);
    }

    @Override
    public List<ForeignKey<SecurityAppRegUserProfileRecord, ?>> getReferences() {
        return Arrays.asList(Keys.FK1_APP_REG_USER_PROFILE_CLNT_ID, Keys.FK2_APP_REG_USER_PROFILE_APP_ID, Keys.FK3_APP_REG_USER_PROFILE_PROFILE_ID, Keys.FK4_APP_REG_USER_PROFILE_CLIENT_TYPE);
    }

    private transient SecurityClientPath _securityClient;

    /**
     * Get the implicit join path to the <code>security.security_client</code>
     * table.
     */
    public SecurityClientPath securityClient() {
        if (_securityClient == null)
            _securityClient = new SecurityClientPath(this, Keys.FK1_APP_REG_USER_PROFILE_CLNT_ID, null);

        return _securityClient;
    }

    private transient SecurityAppPath _securityApp;

    /**
     * Get the implicit join path to the <code>security.security_app</code>
     * table.
     */
    public SecurityAppPath securityApp() {
        if (_securityApp == null)
            _securityApp = new SecurityAppPath(this, Keys.FK2_APP_REG_USER_PROFILE_APP_ID, null);

        return _securityApp;
    }

    private transient SecurityProfilePath _securityProfile;

    /**
     * Get the implicit join path to the <code>security.security_profile</code>
     * table.
     */
    public SecurityProfilePath securityProfile() {
        if (_securityProfile == null)
            _securityProfile = new SecurityProfilePath(this, Keys.FK3_APP_REG_USER_PROFILE_PROFILE_ID, null);

        return _securityProfile;
    }

    private transient SecurityClientTypePath _securityClientType;

    /**
     * Get the implicit join path to the
     * <code>security.security_client_type</code> table.
     */
    public SecurityClientTypePath securityClientType() {
        if (_securityClientType == null)
            _securityClientType = new SecurityClientTypePath(this, Keys.FK4_APP_REG_USER_PROFILE_CLIENT_TYPE, null);

        return _securityClientType;
    }

    @Override
    public SecurityAppRegUserProfile as(String alias) {
        return new SecurityAppRegUserProfile(DSL.name(alias), this);
    }

    @Override
    public SecurityAppRegUserProfile as(Name alias) {
        return new SecurityAppRegUserProfile(alias, this);
    }

    @Override
    public SecurityAppRegUserProfile as(Table<?> alias) {
        return new SecurityAppRegUserProfile(alias.getQualifiedName(), this);
    }

    /**
     * Rename this table
     */
    @Override
    public SecurityAppRegUserProfile rename(String name) {
        return new SecurityAppRegUserProfile(DSL.name(name), null);
    }

    /**
     * Rename this table
     */
    @Override
    public SecurityAppRegUserProfile rename(Name name) {
        return new SecurityAppRegUserProfile(name, null);
    }

    /**
     * Rename this table
     */
    @Override
    public SecurityAppRegUserProfile rename(Table<?> name) {
        return new SecurityAppRegUserProfile(name.getQualifiedName(), null);
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public SecurityAppRegUserProfile where(Condition condition) {
        return new SecurityAppRegUserProfile(getQualifiedName(), aliased() ? this : null, null, condition);
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public SecurityAppRegUserProfile where(Collection<? extends Condition> conditions) {
        return where(DSL.and(conditions));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public SecurityAppRegUserProfile where(Condition... conditions) {
        return where(DSL.and(conditions));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public SecurityAppRegUserProfile where(Field<Boolean> condition) {
        return where(DSL.condition(condition));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    @PlainSQL
    public SecurityAppRegUserProfile where(SQL condition) {
        return where(DSL.condition(condition));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    @PlainSQL
    public SecurityAppRegUserProfile where(@Stringly.SQL String condition) {
        return where(DSL.condition(condition));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    @PlainSQL
    public SecurityAppRegUserProfile where(@Stringly.SQL String condition, Object... binds) {
        return where(DSL.condition(condition, binds));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    @PlainSQL
    public SecurityAppRegUserProfile where(@Stringly.SQL String condition, QueryPart... parts) {
        return where(DSL.condition(condition, parts));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public SecurityAppRegUserProfile whereExists(Select<?> select) {
        return where(DSL.exists(select));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public SecurityAppRegUserProfile whereNotExists(Select<?> select) {
        return where(DSL.notExists(select));
    }
}
