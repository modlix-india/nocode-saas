/*
 * This file is generated by jOOQ.
 */
package com.fincity.security.jooq.tables;


import com.fincity.security.jooq.Keys;
import com.fincity.security.jooq.Security;
import com.fincity.security.jooq.enums.SecurityAppRegProfileRestrictionLevel;
import com.fincity.security.jooq.tables.SecurityApp.SecurityAppPath;
import com.fincity.security.jooq.tables.SecurityClient.SecurityClientPath;
import com.fincity.security.jooq.tables.SecurityClientType.SecurityClientTypePath;
import com.fincity.security.jooq.tables.SecurityProfile.SecurityProfilePath;
import com.fincity.security.jooq.tables.records.SecurityAppRegProfileRestrictionRecord;

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
public class SecurityAppRegProfileRestriction extends TableImpl<SecurityAppRegProfileRestrictionRecord> {

    private static final long serialVersionUID = 1L;

    /**
     * The reference instance of
     * <code>security.security_app_reg_profile_restriction</code>
     */
    public static final SecurityAppRegProfileRestriction SECURITY_APP_REG_PROFILE_RESTRICTION = new SecurityAppRegProfileRestriction();

    /**
     * The class holding records for this type
     */
    @Override
    public Class<SecurityAppRegProfileRestrictionRecord> getRecordType() {
        return SecurityAppRegProfileRestrictionRecord.class;
    }

    /**
     * The column <code>security.security_app_reg_profile_restriction.ID</code>.
     * Primary key
     */
    public final TableField<SecurityAppRegProfileRestrictionRecord, ULong> ID = createField(DSL.name("ID"), SQLDataType.BIGINTUNSIGNED.nullable(false).identity(true), this, "Primary key");

    /**
     * The column
     * <code>security.security_app_reg_profile_restriction.CLIENT_ID</code>.
     * Client ID
     */
    public final TableField<SecurityAppRegProfileRestrictionRecord, ULong> CLIENT_ID = createField(DSL.name("CLIENT_ID"), SQLDataType.BIGINTUNSIGNED.nullable(false), this, "Client ID");

    /**
     * The column
     * <code>security.security_app_reg_profile_restriction.CLIENT_TYPE</code>.
     * Client type
     */
    public final TableField<SecurityAppRegProfileRestrictionRecord, String> CLIENT_TYPE = createField(DSL.name("CLIENT_TYPE"), SQLDataType.CHAR(4).nullable(false).defaultValue(DSL.inline("BUS", SQLDataType.CHAR)), this, "Client type");

    /**
     * The column
     * <code>security.security_app_reg_profile_restriction.APP_ID</code>. App ID
     */
    public final TableField<SecurityAppRegProfileRestrictionRecord, ULong> APP_ID = createField(DSL.name("APP_ID"), SQLDataType.BIGINTUNSIGNED.nullable(false), this, "App ID");

    /**
     * The column
     * <code>security.security_app_reg_profile_restriction.LEVEL</code>. Access
     * level
     */
    public final TableField<SecurityAppRegProfileRestrictionRecord, SecurityAppRegProfileRestrictionLevel> LEVEL = createField(DSL.name("LEVEL"), SQLDataType.VARCHAR(8).nullable(false).defaultValue(DSL.inline("CLIENT", SQLDataType.VARCHAR)).asEnumDataType(SecurityAppRegProfileRestrictionLevel.class), this, "Access level");

    /**
     * The column
     * <code>security.security_app_reg_profile_restriction.BUSINESS_TYPE</code>.
     * Business type
     */
    public final TableField<SecurityAppRegProfileRestrictionRecord, String> BUSINESS_TYPE = createField(DSL.name("BUSINESS_TYPE"), SQLDataType.CHAR(10).nullable(false).defaultValue(DSL.inline("COMMON", SQLDataType.CHAR)), this, "Business type");

    /**
     * The column
     * <code>security.security_app_reg_profile_restriction.PROFILE_ID</code>.
     * Profile ID
     */
    public final TableField<SecurityAppRegProfileRestrictionRecord, ULong> PROFILE_ID = createField(DSL.name("PROFILE_ID"), SQLDataType.BIGINTUNSIGNED.nullable(false), this, "Profile ID");

    /**
     * The column
     * <code>security.security_app_reg_profile_restriction.CREATED_BY</code>. ID
     * of the user who created this row
     */
    public final TableField<SecurityAppRegProfileRestrictionRecord, ULong> CREATED_BY = createField(DSL.name("CREATED_BY"), SQLDataType.BIGINTUNSIGNED, this, "ID of the user who created this row");

    /**
     * The column
     * <code>security.security_app_reg_profile_restriction.CREATED_AT</code>.
     * Time when this row is created
     */
    public final TableField<SecurityAppRegProfileRestrictionRecord, LocalDateTime> CREATED_AT = createField(DSL.name("CREATED_AT"), SQLDataType.LOCALDATETIME(0).nullable(false).defaultValue(DSL.field(DSL.raw("CURRENT_TIMESTAMP"), SQLDataType.LOCALDATETIME)), this, "Time when this row is created");

    private SecurityAppRegProfileRestriction(Name alias, Table<SecurityAppRegProfileRestrictionRecord> aliased) {
        this(alias, aliased, (Field<?>[]) null, null);
    }

    private SecurityAppRegProfileRestriction(Name alias, Table<SecurityAppRegProfileRestrictionRecord> aliased, Field<?>[] parameters, Condition where) {
        super(alias, null, aliased, parameters, DSL.comment(""), TableOptions.table(), where);
    }

    /**
     * Create an aliased
     * <code>security.security_app_reg_profile_restriction</code> table
     * reference
     */
    public SecurityAppRegProfileRestriction(String alias) {
        this(DSL.name(alias), SECURITY_APP_REG_PROFILE_RESTRICTION);
    }

    /**
     * Create an aliased
     * <code>security.security_app_reg_profile_restriction</code> table
     * reference
     */
    public SecurityAppRegProfileRestriction(Name alias) {
        this(alias, SECURITY_APP_REG_PROFILE_RESTRICTION);
    }

    /**
     * Create a <code>security.security_app_reg_profile_restriction</code> table
     * reference
     */
    public SecurityAppRegProfileRestriction() {
        this(DSL.name("security_app_reg_profile_restriction"), null);
    }

    public <O extends Record> SecurityAppRegProfileRestriction(Table<O> path, ForeignKey<O, SecurityAppRegProfileRestrictionRecord> childPath, InverseForeignKey<O, SecurityAppRegProfileRestrictionRecord> parentPath) {
        super(path, childPath, parentPath, SECURITY_APP_REG_PROFILE_RESTRICTION);
    }

    /**
     * A subtype implementing {@link Path} for simplified path-based joins.
     */
    public static class SecurityAppRegProfileRestrictionPath extends SecurityAppRegProfileRestriction implements Path<SecurityAppRegProfileRestrictionRecord> {

        private static final long serialVersionUID = 1L;
        public <O extends Record> SecurityAppRegProfileRestrictionPath(Table<O> path, ForeignKey<O, SecurityAppRegProfileRestrictionRecord> childPath, InverseForeignKey<O, SecurityAppRegProfileRestrictionRecord> parentPath) {
            super(path, childPath, parentPath);
        }
        private SecurityAppRegProfileRestrictionPath(Name alias, Table<SecurityAppRegProfileRestrictionRecord> aliased) {
            super(alias, aliased);
        }

        @Override
        public SecurityAppRegProfileRestrictionPath as(String alias) {
            return new SecurityAppRegProfileRestrictionPath(DSL.name(alias), this);
        }

        @Override
        public SecurityAppRegProfileRestrictionPath as(Name alias) {
            return new SecurityAppRegProfileRestrictionPath(alias, this);
        }

        @Override
        public SecurityAppRegProfileRestrictionPath as(Table<?> alias) {
            return new SecurityAppRegProfileRestrictionPath(alias.getQualifiedName(), this);
        }
    }

    @Override
    public Schema getSchema() {
        return aliased() ? null : Security.SECURITY;
    }

    @Override
    public Identity<SecurityAppRegProfileRestrictionRecord, ULong> getIdentity() {
        return (Identity<SecurityAppRegProfileRestrictionRecord, ULong>) super.getIdentity();
    }

    @Override
    public UniqueKey<SecurityAppRegProfileRestrictionRecord> getPrimaryKey() {
        return Keys.KEY_SECURITY_APP_REG_PROFILE_RESTRICTION_PRIMARY;
    }

    @Override
    public List<UniqueKey<SecurityAppRegProfileRestrictionRecord>> getUniqueKeys() {
        return Arrays.asList(Keys.KEY_SECURITY_APP_REG_PROFILE_RESTRICTION_CLIENT_ID);
    }

    @Override
    public List<ForeignKey<SecurityAppRegProfileRestrictionRecord, ?>> getReferences() {
        return Arrays.asList(Keys.FK1_APP_REG_PROFILE_CLNT_ID, Keys.FK2_APP_REG_PROFILE_APP_ID, Keys.FK3_APP_REG_PROFILE_PROFILE_ID, Keys.FK4_APP_REG_PROFILE_CLIENT_TYPE);
    }

    private transient SecurityClientPath _securityClient;

    /**
     * Get the implicit join path to the <code>security.security_client</code>
     * table.
     */
    public SecurityClientPath securityClient() {
        if (_securityClient == null)
            _securityClient = new SecurityClientPath(this, Keys.FK1_APP_REG_PROFILE_CLNT_ID, null);

        return _securityClient;
    }

    private transient SecurityAppPath _securityApp;

    /**
     * Get the implicit join path to the <code>security.security_app</code>
     * table.
     */
    public SecurityAppPath securityApp() {
        if (_securityApp == null)
            _securityApp = new SecurityAppPath(this, Keys.FK2_APP_REG_PROFILE_APP_ID, null);

        return _securityApp;
    }

    private transient SecurityProfilePath _securityProfile;

    /**
     * Get the implicit join path to the <code>security.security_profile</code>
     * table.
     */
    public SecurityProfilePath securityProfile() {
        if (_securityProfile == null)
            _securityProfile = new SecurityProfilePath(this, Keys.FK3_APP_REG_PROFILE_PROFILE_ID, null);

        return _securityProfile;
    }

    private transient SecurityClientTypePath _securityClientType;

    /**
     * Get the implicit join path to the
     * <code>security.security_client_type</code> table.
     */
    public SecurityClientTypePath securityClientType() {
        if (_securityClientType == null)
            _securityClientType = new SecurityClientTypePath(this, Keys.FK4_APP_REG_PROFILE_CLIENT_TYPE, null);

        return _securityClientType;
    }

    @Override
    public SecurityAppRegProfileRestriction as(String alias) {
        return new SecurityAppRegProfileRestriction(DSL.name(alias), this);
    }

    @Override
    public SecurityAppRegProfileRestriction as(Name alias) {
        return new SecurityAppRegProfileRestriction(alias, this);
    }

    @Override
    public SecurityAppRegProfileRestriction as(Table<?> alias) {
        return new SecurityAppRegProfileRestriction(alias.getQualifiedName(), this);
    }

    /**
     * Rename this table
     */
    @Override
    public SecurityAppRegProfileRestriction rename(String name) {
        return new SecurityAppRegProfileRestriction(DSL.name(name), null);
    }

    /**
     * Rename this table
     */
    @Override
    public SecurityAppRegProfileRestriction rename(Name name) {
        return new SecurityAppRegProfileRestriction(name, null);
    }

    /**
     * Rename this table
     */
    @Override
    public SecurityAppRegProfileRestriction rename(Table<?> name) {
        return new SecurityAppRegProfileRestriction(name.getQualifiedName(), null);
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public SecurityAppRegProfileRestriction where(Condition condition) {
        return new SecurityAppRegProfileRestriction(getQualifiedName(), aliased() ? this : null, null, condition);
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public SecurityAppRegProfileRestriction where(Collection<? extends Condition> conditions) {
        return where(DSL.and(conditions));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public SecurityAppRegProfileRestriction where(Condition... conditions) {
        return where(DSL.and(conditions));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public SecurityAppRegProfileRestriction where(Field<Boolean> condition) {
        return where(DSL.condition(condition));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    @PlainSQL
    public SecurityAppRegProfileRestriction where(SQL condition) {
        return where(DSL.condition(condition));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    @PlainSQL
    public SecurityAppRegProfileRestriction where(@Stringly.SQL String condition) {
        return where(DSL.condition(condition));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    @PlainSQL
    public SecurityAppRegProfileRestriction where(@Stringly.SQL String condition, Object... binds) {
        return where(DSL.condition(condition, binds));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    @PlainSQL
    public SecurityAppRegProfileRestriction where(@Stringly.SQL String condition, QueryPart... parts) {
        return where(DSL.condition(condition, parts));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public SecurityAppRegProfileRestriction whereExists(Select<?> select) {
        return where(DSL.exists(select));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public SecurityAppRegProfileRestriction whereNotExists(Select<?> select) {
        return where(DSL.notExists(select));
    }
}
