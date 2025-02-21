/*
 * This file is generated by jOOQ.
 */
package com.fincity.security.jooq.tables;


import com.fincity.security.jooq.Keys;
import com.fincity.security.jooq.Security;
import com.fincity.security.jooq.enums.SecurityAppAppAccessType;
import com.fincity.security.jooq.enums.SecurityAppAppType;
import com.fincity.security.jooq.enums.SecurityAppAppUsageType;
import com.fincity.security.jooq.tables.SecurityAppAccess.SecurityAppAccessPath;
import com.fincity.security.jooq.tables.SecurityAppDependency.SecurityAppDependencyPath;
import com.fincity.security.jooq.tables.SecurityAppProperty.SecurityAppPropertyPath;
import com.fincity.security.jooq.tables.SecurityAppRegAccess.SecurityAppRegAccessPath;
import com.fincity.security.jooq.tables.SecurityAppRegFileAccess.SecurityAppRegFileAccessPath;
import com.fincity.security.jooq.tables.SecurityAppRegIntegration.SecurityAppRegIntegrationPath;
import com.fincity.security.jooq.tables.SecurityAppRegPackage.SecurityAppRegPackagePath;
import com.fincity.security.jooq.tables.SecurityAppRegUserRole.SecurityAppRegUserRolePath;
import com.fincity.security.jooq.tables.SecurityClient.SecurityClientPath;
import com.fincity.security.jooq.tables.SecurityClientOtpPolicy.SecurityClientOtpPolicyPath;
import com.fincity.security.jooq.tables.SecurityClientPasswordPolicy.SecurityClientPasswordPolicyPath;
import com.fincity.security.jooq.tables.SecurityClientPinPolicy.SecurityClientPinPolicyPath;
import com.fincity.security.jooq.tables.SecurityClientUrl.SecurityClientUrlPath;
import com.fincity.security.jooq.tables.SecurityOtp.SecurityOtpPath;
import com.fincity.security.jooq.tables.SecurityPackage.SecurityPackagePath;
import com.fincity.security.jooq.tables.SecurityPermission.SecurityPermissionPath;
import com.fincity.security.jooq.tables.SecurityRole.SecurityRolePath;
import com.fincity.security.jooq.tables.records.SecurityAppRecord;

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
public class SecurityApp extends TableImpl<SecurityAppRecord> {

    private static final long serialVersionUID = 1L;

    /**
     * The reference instance of <code>security.security_app</code>
     */
    public static final SecurityApp SECURITY_APP = new SecurityApp();

    /**
     * The class holding records for this type
     */
    @Override
    public Class<SecurityAppRecord> getRecordType() {
        return SecurityAppRecord.class;
    }

    /**
     * The column <code>security.security_app.ID</code>. Primary key
     */
    public final TableField<SecurityAppRecord, ULong> ID = createField(DSL.name("ID"), SQLDataType.BIGINTUNSIGNED.nullable(false).identity(true), this, "Primary key");

    /**
     * The column <code>security.security_app.CLIENT_ID</code>. Client ID
     */
    public final TableField<SecurityAppRecord, ULong> CLIENT_ID = createField(DSL.name("CLIENT_ID"), SQLDataType.BIGINTUNSIGNED.nullable(false), this, "Client ID");

    /**
     * The column <code>security.security_app.APP_NAME</code>. Name of the
     * application
     */
    public final TableField<SecurityAppRecord, String> APP_NAME = createField(DSL.name("APP_NAME"), SQLDataType.VARCHAR(512).nullable(false), this, "Name of the application");

    /**
     * The column <code>security.security_app.APP_CODE</code>. Code of the
     * application
     */
    public final TableField<SecurityAppRecord, String> APP_CODE = createField(DSL.name("APP_CODE"), SQLDataType.CHAR(64).nullable(false), this, "Code of the application");

    /**
     * The column <code>security.security_app.APP_TYPE</code>. Application type
     */
    public final TableField<SecurityAppRecord, SecurityAppAppType> APP_TYPE = createField(DSL.name("APP_TYPE"), SQLDataType.VARCHAR(6).nullable(false).defaultValue(DSL.inline("APP", SQLDataType.VARCHAR)).asEnumDataType(SecurityAppAppType.class), this, "Application type");

    /**
     * The column <code>security.security_app.APP_ACCESS_TYPE</code>.
     */
    public final TableField<SecurityAppRecord, SecurityAppAppAccessType> APP_ACCESS_TYPE = createField(DSL.name("APP_ACCESS_TYPE"), SQLDataType.VARCHAR(8).nullable(false).defaultValue(DSL.inline("OWN", SQLDataType.VARCHAR)).asEnumDataType(SecurityAppAppAccessType.class), this, "");

    /**
     * The column <code>security.security_app.THUMB_URL</code>.
     */
    public final TableField<SecurityAppRecord, String> THUMB_URL = createField(DSL.name("THUMB_URL"), SQLDataType.VARCHAR(1024), this, "");

    /**
     * The column <code>security.security_app.APP_USAGE_TYPE</code>. S -
     * Standalone (Mostly for sites), B - Business only, B to C Consumer, B
     * Business, X Any, and so on so forth.
     */
    public final TableField<SecurityAppRecord, SecurityAppAppUsageType> APP_USAGE_TYPE = createField(DSL.name("APP_USAGE_TYPE"), SQLDataType.VARCHAR(5).nullable(false).defaultValue(DSL.inline("S", SQLDataType.VARCHAR)).asEnumDataType(SecurityAppAppUsageType.class), this, "S - Standalone (Mostly for sites), B - Business only, B to C Consumer, B Business, X Any, and so on so forth.");

    /**
     * The column <code>security.security_app.CREATED_BY</code>. ID of the user
     * who created this row
     */
    public final TableField<SecurityAppRecord, ULong> CREATED_BY = createField(DSL.name("CREATED_BY"), SQLDataType.BIGINTUNSIGNED, this, "ID of the user who created this row");

    /**
     * The column <code>security.security_app.CREATED_AT</code>. Time when this
     * row is created
     */
    public final TableField<SecurityAppRecord, LocalDateTime> CREATED_AT = createField(DSL.name("CREATED_AT"), SQLDataType.LOCALDATETIME(0).nullable(false).defaultValue(DSL.field(DSL.raw("CURRENT_TIMESTAMP"), SQLDataType.LOCALDATETIME)), this, "Time when this row is created");

    /**
     * The column <code>security.security_app.UPDATED_BY</code>. ID of the user
     * who updated this row
     */
    public final TableField<SecurityAppRecord, ULong> UPDATED_BY = createField(DSL.name("UPDATED_BY"), SQLDataType.BIGINTUNSIGNED, this, "ID of the user who updated this row");

    /**
     * The column <code>security.security_app.UPDATED_AT</code>. Time when this
     * row is updated
     */
    public final TableField<SecurityAppRecord, LocalDateTime> UPDATED_AT = createField(DSL.name("UPDATED_AT"), SQLDataType.LOCALDATETIME(0).nullable(false).defaultValue(DSL.field(DSL.raw("CURRENT_TIMESTAMP"), SQLDataType.LOCALDATETIME)), this, "Time when this row is updated");

    private SecurityApp(Name alias, Table<SecurityAppRecord> aliased) {
        this(alias, aliased, (Field<?>[]) null, null);
    }

    private SecurityApp(Name alias, Table<SecurityAppRecord> aliased, Field<?>[] parameters, Condition where) {
        super(alias, null, aliased, parameters, DSL.comment(""), TableOptions.table(), where);
    }

    /**
     * Create an aliased <code>security.security_app</code> table reference
     */
    public SecurityApp(String alias) {
        this(DSL.name(alias), SECURITY_APP);
    }

    /**
     * Create an aliased <code>security.security_app</code> table reference
     */
    public SecurityApp(Name alias) {
        this(alias, SECURITY_APP);
    }

    /**
     * Create a <code>security.security_app</code> table reference
     */
    public SecurityApp() {
        this(DSL.name("security_app"), null);
    }

    public <O extends Record> SecurityApp(Table<O> path, ForeignKey<O, SecurityAppRecord> childPath, InverseForeignKey<O, SecurityAppRecord> parentPath) {
        super(path, childPath, parentPath, SECURITY_APP);
    }

    /**
     * A subtype implementing {@link Path} for simplified path-based joins.
     */
    public static class SecurityAppPath extends SecurityApp implements Path<SecurityAppRecord> {

        private static final long serialVersionUID = 1L;
        public <O extends Record> SecurityAppPath(Table<O> path, ForeignKey<O, SecurityAppRecord> childPath, InverseForeignKey<O, SecurityAppRecord> parentPath) {
            super(path, childPath, parentPath);
        }
        private SecurityAppPath(Name alias, Table<SecurityAppRecord> aliased) {
            super(alias, aliased);
        }

        @Override
        public SecurityAppPath as(String alias) {
            return new SecurityAppPath(DSL.name(alias), this);
        }

        @Override
        public SecurityAppPath as(Name alias) {
            return new SecurityAppPath(alias, this);
        }

        @Override
        public SecurityAppPath as(Table<?> alias) {
            return new SecurityAppPath(alias.getQualifiedName(), this);
        }
    }

    @Override
    public Schema getSchema() {
        return aliased() ? null : Security.SECURITY;
    }

    @Override
    public Identity<SecurityAppRecord, ULong> getIdentity() {
        return (Identity<SecurityAppRecord, ULong>) super.getIdentity();
    }

    @Override
    public UniqueKey<SecurityAppRecord> getPrimaryKey() {
        return Keys.KEY_SECURITY_APP_PRIMARY;
    }

    @Override
    public List<UniqueKey<SecurityAppRecord>> getUniqueKeys() {
        return Arrays.asList(Keys.KEY_SECURITY_APP_UK1_APPCODE);
    }

    @Override
    public List<ForeignKey<SecurityAppRecord, ?>> getReferences() {
        return Arrays.asList(Keys.FK1_APP_CLIENT_ID);
    }

    private transient SecurityClientPath _securityClient;

    /**
     * Get the implicit join path to the <code>security.security_client</code>
     * table.
     */
    public SecurityClientPath securityClient() {
        if (_securityClient == null)
            _securityClient = new SecurityClientPath(this, Keys.FK1_APP_CLIENT_ID, null);

        return _securityClient;
    }

    private transient SecurityAppAccessPath _securityAppAccess;

    /**
     * Get the implicit to-many join path to the
     * <code>security.security_app_access</code> table
     */
    public SecurityAppAccessPath securityAppAccess() {
        if (_securityAppAccess == null)
            _securityAppAccess = new SecurityAppAccessPath(this, null, Keys.FK1_APP_ACCESS_APP_ID.getInverseKey());

        return _securityAppAccess;
    }

    private transient SecurityAppDependencyPath _fk1AppDepAppId;

    /**
     * Get the implicit to-many join path to the
     * <code>security.security_app_dependency</code> table, via the
     * <code>FK1_APP_DEP_APP_ID</code> key
     */
    public SecurityAppDependencyPath fk1AppDepAppId() {
        if (_fk1AppDepAppId == null)
            _fk1AppDepAppId = new SecurityAppDependencyPath(this, null, Keys.FK1_APP_DEP_APP_ID.getInverseKey());

        return _fk1AppDepAppId;
    }

    private transient SecurityAppPropertyPath _securityAppProperty;

    /**
     * Get the implicit to-many join path to the
     * <code>security.security_app_property</code> table
     */
    public SecurityAppPropertyPath securityAppProperty() {
        if (_securityAppProperty == null)
            _securityAppProperty = new SecurityAppPropertyPath(this, null, Keys.FK1_APP_PROP_APP_ID.getInverseKey());

        return _securityAppProperty;
    }

    private transient SecurityAppRegIntegrationPath _securityAppRegIntegration;

    /**
     * Get the implicit to-many join path to the
     * <code>security.security_app_reg_integration</code> table
     */
    public SecurityAppRegIntegrationPath securityAppRegIntegration() {
        if (_securityAppRegIntegration == null)
            _securityAppRegIntegration = new SecurityAppRegIntegrationPath(this, null, Keys.FK1_APP_REG_INTEGRATION_APP_ID.getInverseKey());

        return _securityAppRegIntegration;
    }

    private transient SecurityClientUrlPath _securityClientUrl;

    /**
     * Get the implicit to-many join path to the
     * <code>security.security_client_url</code> table
     */
    public SecurityClientUrlPath securityClientUrl() {
        if (_securityClientUrl == null)
            _securityClientUrl = new SecurityClientUrlPath(this, null, Keys.FK1_CLIENT_URL_APP_CODE.getInverseKey());

        return _securityClientUrl;
    }

    private transient SecurityOtpPath _securityOtp;

    /**
     * Get the implicit to-many join path to the
     * <code>security.security_otp</code> table
     */
    public SecurityOtpPath securityOtp() {
        if (_securityOtp == null)
            _securityOtp = new SecurityOtpPath(this, null, Keys.FK1_OTP_APP_ID.getInverseKey());

        return _securityOtp;
    }

    private transient SecurityAppDependencyPath _fk2AppDepDepAppId;

    /**
     * Get the implicit to-many join path to the
     * <code>security.security_app_dependency</code> table, via the
     * <code>FK2_APP_DEP_DEP_APP_ID</code> key
     */
    public SecurityAppDependencyPath fk2AppDepDepAppId() {
        if (_fk2AppDepDepAppId == null)
            _fk2AppDepDepAppId = new SecurityAppDependencyPath(this, null, Keys.FK2_APP_DEP_DEP_APP_ID.getInverseKey());

        return _fk2AppDepDepAppId;
    }

    private transient SecurityAppRegAccessPath _fk2AppRegAccAppId;

    /**
     * Get the implicit to-many join path to the
     * <code>security.security_app_reg_access</code> table, via the
     * <code>FK2_APP_REG_ACC_APP_ID</code> key
     */
    public SecurityAppRegAccessPath fk2AppRegAccAppId() {
        if (_fk2AppRegAccAppId == null)
            _fk2AppRegAccAppId = new SecurityAppRegAccessPath(this, null, Keys.FK2_APP_REG_ACC_APP_ID.getInverseKey());

        return _fk2AppRegAccAppId;
    }

    private transient SecurityAppRegFileAccessPath _securityAppRegFileAccess;

    /**
     * Get the implicit to-many join path to the
     * <code>security.security_app_reg_file_access</code> table
     */
    public SecurityAppRegFileAccessPath securityAppRegFileAccess() {
        if (_securityAppRegFileAccess == null)
            _securityAppRegFileAccess = new SecurityAppRegFileAccessPath(this, null, Keys.FK2_APP_REG_FILE_ACC_APP_ID.getInverseKey());

        return _securityAppRegFileAccess;
    }

    private transient SecurityAppRegPackagePath _securityAppRegPackage;

    /**
     * Get the implicit to-many join path to the
     * <code>security.security_app_reg_package</code> table
     */
    public SecurityAppRegPackagePath securityAppRegPackage() {
        if (_securityAppRegPackage == null)
            _securityAppRegPackage = new SecurityAppRegPackagePath(this, null, Keys.FK2_APP_REG_PKG_APP_ID.getInverseKey());

        return _securityAppRegPackage;
    }

    private transient SecurityAppRegUserRolePath _securityAppRegUserRole;

    /**
     * Get the implicit to-many join path to the
     * <code>security.security_app_reg_user_role</code> table
     */
    public SecurityAppRegUserRolePath securityAppRegUserRole() {
        if (_securityAppRegUserRole == null)
            _securityAppRegUserRole = new SecurityAppRegUserRolePath(this, null, Keys.FK2_APP_REG_ROLE_APP_ID.getInverseKey());

        return _securityAppRegUserRole;
    }

    private transient SecurityClientOtpPolicyPath _securityClientOtpPolicy;

    /**
     * Get the implicit to-many join path to the
     * <code>security.security_client_otp_policy</code> table
     */
    public SecurityClientOtpPolicyPath securityClientOtpPolicy() {
        if (_securityClientOtpPolicy == null)
            _securityClientOtpPolicy = new SecurityClientOtpPolicyPath(this, null, Keys.FK2_CLIENT_OTP_POL_APP_ID.getInverseKey());

        return _securityClientOtpPolicy;
    }

    private transient SecurityClientPinPolicyPath _securityClientPinPolicy;

    /**
     * Get the implicit to-many join path to the
     * <code>security.security_client_pin_policy</code> table
     */
    public SecurityClientPinPolicyPath securityClientPinPolicy() {
        if (_securityClientPinPolicy == null)
            _securityClientPinPolicy = new SecurityClientPinPolicyPath(this, null, Keys.FK2_CLIENT_PIN_POL_APP_ID.getInverseKey());

        return _securityClientPinPolicy;
    }

    private transient SecurityClientPasswordPolicyPath _securityClientPasswordPolicy;

    /**
     * Get the implicit to-many join path to the
     * <code>security.security_client_password_policy</code> table
     */
    public SecurityClientPasswordPolicyPath securityClientPasswordPolicy() {
        if (_securityClientPasswordPolicy == null)
            _securityClientPasswordPolicy = new SecurityClientPasswordPolicyPath(this, null, Keys.FK2_CLIENT_PWD_POL_APP_ID.getInverseKey());

        return _securityClientPasswordPolicy;
    }

    private transient SecurityPackagePath _securityPackage;

    /**
     * Get the implicit to-many join path to the
     * <code>security.security_package</code> table
     */
    public SecurityPackagePath securityPackage() {
        if (_securityPackage == null)
            _securityPackage = new SecurityPackagePath(this, null, Keys.FK2_PACKAGE_APP_ID.getInverseKey());

        return _securityPackage;
    }

    private transient SecurityPermissionPath _securityPermission;

    /**
     * Get the implicit to-many join path to the
     * <code>security.security_permission</code> table
     */
    public SecurityPermissionPath securityPermission() {
        if (_securityPermission == null)
            _securityPermission = new SecurityPermissionPath(this, null, Keys.FK2_PERMISSION_APP_ID.getInverseKey());

        return _securityPermission;
    }

    private transient SecurityRolePath _securityRole;

    /**
     * Get the implicit to-many join path to the
     * <code>security.security_role</code> table
     */
    public SecurityRolePath securityRole() {
        if (_securityRole == null)
            _securityRole = new SecurityRolePath(this, null, Keys.FK2_ROLE_APP_ID.getInverseKey());

        return _securityRole;
    }

    private transient SecurityAppRegAccessPath _fk3AppRegAccAllowAppId;

    /**
     * Get the implicit to-many join path to the
     * <code>security.security_app_reg_access</code> table, via the
     * <code>FK3_APP_REG_ACC_ALLOW_APP_ID</code> key
     */
    public SecurityAppRegAccessPath fk3AppRegAccAllowAppId() {
        if (_fk3AppRegAccAllowAppId == null)
            _fk3AppRegAccAllowAppId = new SecurityAppRegAccessPath(this, null, Keys.FK3_APP_REG_ACC_ALLOW_APP_ID.getInverseKey());

        return _fk3AppRegAccAllowAppId;
    }

    /**
     * Get the implicit many-to-many join path to the
     * <code>security.security_client</code> table, via the
     * <code>FK1_APP_ACCESS_CLIENT_ID</code> key
     */
    public SecurityClientPath fk1AppAccessClientId() {
        return securityAppAccess().securityClient();
    }

    /**
     * Get the implicit many-to-many join path to the
     * <code>security.security_client</code> table, via the
     * <code>FK1_CLIENT_OTP_POL_CLIENT_ID</code> key
     */
    public SecurityClientPath fk1ClientOtpPolClientId() {
        return securityClientOtpPolicy().securityClient();
    }

    /**
     * Get the implicit many-to-many join path to the
     * <code>security.security_client</code> table, via the
     * <code>FK1_CLIENT_PWD_POL_CLIENT_ID</code> key
     */
    public SecurityClientPath fk1ClientPwdPolClientId() {
        return securityClientPasswordPolicy().securityClient();
    }

    /**
     * Get the implicit many-to-many join path to the
     * <code>security.security_client</code> table, via the
     * <code>FK1_CLIENT_PIN_POL_CLIENT_ID</code> key
     */
    public SecurityClientPath fk1ClientPinPolClientId() {
        return securityClientPinPolicy().securityClient();
    }

    @Override
    public SecurityApp as(String alias) {
        return new SecurityApp(DSL.name(alias), this);
    }

    @Override
    public SecurityApp as(Name alias) {
        return new SecurityApp(alias, this);
    }

    @Override
    public SecurityApp as(Table<?> alias) {
        return new SecurityApp(alias.getQualifiedName(), this);
    }

    /**
     * Rename this table
     */
    @Override
    public SecurityApp rename(String name) {
        return new SecurityApp(DSL.name(name), null);
    }

    /**
     * Rename this table
     */
    @Override
    public SecurityApp rename(Name name) {
        return new SecurityApp(name, null);
    }

    /**
     * Rename this table
     */
    @Override
    public SecurityApp rename(Table<?> name) {
        return new SecurityApp(name.getQualifiedName(), null);
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public SecurityApp where(Condition condition) {
        return new SecurityApp(getQualifiedName(), aliased() ? this : null, null, condition);
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public SecurityApp where(Collection<? extends Condition> conditions) {
        return where(DSL.and(conditions));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public SecurityApp where(Condition... conditions) {
        return where(DSL.and(conditions));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public SecurityApp where(Field<Boolean> condition) {
        return where(DSL.condition(condition));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    @PlainSQL
    public SecurityApp where(SQL condition) {
        return where(DSL.condition(condition));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    @PlainSQL
    public SecurityApp where(@Stringly.SQL String condition) {
        return where(DSL.condition(condition));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    @PlainSQL
    public SecurityApp where(@Stringly.SQL String condition, Object... binds) {
        return where(DSL.condition(condition, binds));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    @PlainSQL
    public SecurityApp where(@Stringly.SQL String condition, QueryPart... parts) {
        return where(DSL.condition(condition, parts));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public SecurityApp whereExists(Select<?> select) {
        return where(DSL.exists(select));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public SecurityApp whereNotExists(Select<?> select) {
        return where(DSL.notExists(select));
    }
}
