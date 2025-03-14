/*
 * This file is generated by jOOQ.
 */
package com.fincity.security.jooq.tables;


import com.fincity.security.jooq.Keys;
import com.fincity.security.jooq.Security;
import com.fincity.security.jooq.enums.SecurityClientStatusCode;
import com.fincity.security.jooq.tables.SecurityApp.SecurityAppPath;
import com.fincity.security.jooq.tables.SecurityAppAccess.SecurityAppAccessPath;
import com.fincity.security.jooq.tables.SecurityAppProperty.SecurityAppPropertyPath;
import com.fincity.security.jooq.tables.SecurityAppRegAccess.SecurityAppRegAccessPath;
import com.fincity.security.jooq.tables.SecurityAppRegFileAccess.SecurityAppRegFileAccessPath;
import com.fincity.security.jooq.tables.SecurityAppRegIntegration.SecurityAppRegIntegrationPath;
import com.fincity.security.jooq.tables.SecurityAppRegPackage.SecurityAppRegPackagePath;
import com.fincity.security.jooq.tables.SecurityAppRegUserRole.SecurityAppRegUserRolePath;
import com.fincity.security.jooq.tables.SecurityClientAddress.SecurityClientAddressPath;
import com.fincity.security.jooq.tables.SecurityClientHierarchy.SecurityClientHierarchyPath;
import com.fincity.security.jooq.tables.SecurityClientOtpPolicy.SecurityClientOtpPolicyPath;
import com.fincity.security.jooq.tables.SecurityClientPackage.SecurityClientPackagePath;
import com.fincity.security.jooq.tables.SecurityClientPasswordPolicy.SecurityClientPasswordPolicyPath;
import com.fincity.security.jooq.tables.SecurityClientPinPolicy.SecurityClientPinPolicyPath;
import com.fincity.security.jooq.tables.SecurityClientType.SecurityClientTypePath;
import com.fincity.security.jooq.tables.SecurityClientUrl.SecurityClientUrlPath;
import com.fincity.security.jooq.tables.SecurityOrgStructure.SecurityOrgStructurePath;
import com.fincity.security.jooq.tables.SecurityPackage.SecurityPackagePath;
import com.fincity.security.jooq.tables.SecurityPermission.SecurityPermissionPath;
import com.fincity.security.jooq.tables.SecurityRole.SecurityRolePath;
import com.fincity.security.jooq.tables.SecurityUser.SecurityUserPath;
import com.fincity.security.jooq.tables.records.SecurityClientRecord;

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
import org.jooq.types.UInteger;
import org.jooq.types.ULong;


/**
 * This class is generated by jOOQ.
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes", "this-escape" })
public class SecurityClient extends TableImpl<SecurityClientRecord> {

    private static final long serialVersionUID = 1L;

    /**
     * The reference instance of <code>security.security_client</code>
     */
    public static final SecurityClient SECURITY_CLIENT = new SecurityClient();

    /**
     * The class holding records for this type
     */
    @Override
    public Class<SecurityClientRecord> getRecordType() {
        return SecurityClientRecord.class;
    }

    /**
     * The column <code>security.security_client.ID</code>. Primary key
     */
    public final TableField<SecurityClientRecord, ULong> ID = createField(DSL.name("ID"), SQLDataType.BIGINTUNSIGNED.nullable(false).identity(true), this, "Primary key");

    /**
     * The column <code>security.security_client.CODE</code>. Client code
     */
    public final TableField<SecurityClientRecord, String> CODE = createField(DSL.name("CODE"), SQLDataType.CHAR(8).nullable(false), this, "Client code");

    /**
     * The column <code>security.security_client.NAME</code>. Name of the client
     */
    public final TableField<SecurityClientRecord, String> NAME = createField(DSL.name("NAME"), SQLDataType.VARCHAR(256).nullable(false), this, "Name of the client");

    /**
     * The column <code>security.security_client.TYPE_CODE</code>. Type of
     * client
     */
    public final TableField<SecurityClientRecord, String> TYPE_CODE = createField(DSL.name("TYPE_CODE"), SQLDataType.CHAR(4).nullable(false), this, "Type of client");

    /**
     * The column <code>security.security_client.TOKEN_VALIDITY_MINUTES</code>.
     * Token validity in minutes
     */
    public final TableField<SecurityClientRecord, UInteger> TOKEN_VALIDITY_MINUTES = createField(DSL.name("TOKEN_VALIDITY_MINUTES"), SQLDataType.INTEGERUNSIGNED.nullable(false).defaultValue(DSL.inline("30", SQLDataType.INTEGERUNSIGNED)), this, "Token validity in minutes");

    /**
     * The column <code>security.security_client.LOCALE_CODE</code>. Client
     * default locale
     */
    public final TableField<SecurityClientRecord, String> LOCALE_CODE = createField(DSL.name("LOCALE_CODE"), SQLDataType.VARCHAR(10).defaultValue(DSL.inline("en-US", SQLDataType.VARCHAR)), this, "Client default locale");

    /**
     * The column <code>security.security_client.STATUS_CODE</code>. Status of
     * the client
     */
    public final TableField<SecurityClientRecord, SecurityClientStatusCode> STATUS_CODE = createField(DSL.name("STATUS_CODE"), SQLDataType.VARCHAR(8).defaultValue(DSL.inline("ACTIVE", SQLDataType.VARCHAR)).asEnumDataType(SecurityClientStatusCode.class), this, "Status of the client");

    /**
     * The column <code>security.security_client.BUSINESS_TYPE</code>. At each
     * llevel of business client, customer and consumer there can be different
     * business types.
     */
    public final TableField<SecurityClientRecord, String> BUSINESS_TYPE = createField(DSL.name("BUSINESS_TYPE"), SQLDataType.CHAR(10).nullable(false).defaultValue(DSL.inline("COMMON", SQLDataType.CHAR)), this, "At each llevel of business client, customer and consumer there can be different business types.");

    /**
     * The column <code>security.security_client.CREATED_BY</code>. ID of the
     * user who created this row
     */
    public final TableField<SecurityClientRecord, ULong> CREATED_BY = createField(DSL.name("CREATED_BY"), SQLDataType.BIGINTUNSIGNED, this, "ID of the user who created this row");

    /**
     * The column <code>security.security_client.CREATED_AT</code>. Time when
     * this row is created
     */
    public final TableField<SecurityClientRecord, LocalDateTime> CREATED_AT = createField(DSL.name("CREATED_AT"), SQLDataType.LOCALDATETIME(0).nullable(false).defaultValue(DSL.field(DSL.raw("CURRENT_TIMESTAMP"), SQLDataType.LOCALDATETIME)), this, "Time when this row is created");

    /**
     * The column <code>security.security_client.UPDATED_BY</code>. ID of the
     * user who updated this row
     */
    public final TableField<SecurityClientRecord, ULong> UPDATED_BY = createField(DSL.name("UPDATED_BY"), SQLDataType.BIGINTUNSIGNED, this, "ID of the user who updated this row");

    /**
     * The column <code>security.security_client.UPDATED_AT</code>. Time when
     * this row is updated
     */
    public final TableField<SecurityClientRecord, LocalDateTime> UPDATED_AT = createField(DSL.name("UPDATED_AT"), SQLDataType.LOCALDATETIME(0).nullable(false).defaultValue(DSL.field(DSL.raw("CURRENT_TIMESTAMP"), SQLDataType.LOCALDATETIME)), this, "Time when this row is updated");

    private SecurityClient(Name alias, Table<SecurityClientRecord> aliased) {
        this(alias, aliased, (Field<?>[]) null, null);
    }

    private SecurityClient(Name alias, Table<SecurityClientRecord> aliased, Field<?>[] parameters, Condition where) {
        super(alias, null, aliased, parameters, DSL.comment(""), TableOptions.table(), where);
    }

    /**
     * Create an aliased <code>security.security_client</code> table reference
     */
    public SecurityClient(String alias) {
        this(DSL.name(alias), SECURITY_CLIENT);
    }

    /**
     * Create an aliased <code>security.security_client</code> table reference
     */
    public SecurityClient(Name alias) {
        this(alias, SECURITY_CLIENT);
    }

    /**
     * Create a <code>security.security_client</code> table reference
     */
    public SecurityClient() {
        this(DSL.name("security_client"), null);
    }

    public <O extends Record> SecurityClient(Table<O> path, ForeignKey<O, SecurityClientRecord> childPath, InverseForeignKey<O, SecurityClientRecord> parentPath) {
        super(path, childPath, parentPath, SECURITY_CLIENT);
    }

    /**
     * A subtype implementing {@link Path} for simplified path-based joins.
     */
    public static class SecurityClientPath extends SecurityClient implements Path<SecurityClientRecord> {

        private static final long serialVersionUID = 1L;
        public <O extends Record> SecurityClientPath(Table<O> path, ForeignKey<O, SecurityClientRecord> childPath, InverseForeignKey<O, SecurityClientRecord> parentPath) {
            super(path, childPath, parentPath);
        }
        private SecurityClientPath(Name alias, Table<SecurityClientRecord> aliased) {
            super(alias, aliased);
        }

        @Override
        public SecurityClientPath as(String alias) {
            return new SecurityClientPath(DSL.name(alias), this);
        }

        @Override
        public SecurityClientPath as(Name alias) {
            return new SecurityClientPath(alias, this);
        }

        @Override
        public SecurityClientPath as(Table<?> alias) {
            return new SecurityClientPath(alias.getQualifiedName(), this);
        }
    }

    @Override
    public Schema getSchema() {
        return aliased() ? null : Security.SECURITY;
    }

    @Override
    public Identity<SecurityClientRecord, ULong> getIdentity() {
        return (Identity<SecurityClientRecord, ULong>) super.getIdentity();
    }

    @Override
    public UniqueKey<SecurityClientRecord> getPrimaryKey() {
        return Keys.KEY_SECURITY_CLIENT_PRIMARY;
    }

    @Override
    public List<UniqueKey<SecurityClientRecord>> getUniqueKeys() {
        return Arrays.asList(Keys.KEY_SECURITY_CLIENT_UK1_CLIENT_CODE);
    }

    @Override
    public List<ForeignKey<SecurityClientRecord, ?>> getReferences() {
        return Arrays.asList(Keys.FK1_CLIENT_CLIENT_TYPE_CODE);
    }

    private transient SecurityClientTypePath _securityClientType;

    /**
     * Get the implicit join path to the
     * <code>security.security_client_type</code> table.
     */
    public SecurityClientTypePath securityClientType() {
        if (_securityClientType == null)
            _securityClientType = new SecurityClientTypePath(this, Keys.FK1_CLIENT_CLIENT_TYPE_CODE, null);

        return _securityClientType;
    }

    private transient SecurityAppAccessPath _securityAppAccess;

    /**
     * Get the implicit to-many join path to the
     * <code>security.security_app_access</code> table
     */
    public SecurityAppAccessPath securityAppAccess() {
        if (_securityAppAccess == null)
            _securityAppAccess = new SecurityAppAccessPath(this, null, Keys.FK1_APP_ACCESS_CLIENT_ID.getInverseKey());

        return _securityAppAccess;
    }

    private transient SecurityAppPath _securityApp;

    /**
     * Get the implicit to-many join path to the
     * <code>security.security_app</code> table
     */
    public SecurityAppPath securityApp() {
        if (_securityApp == null)
            _securityApp = new SecurityAppPath(this, null, Keys.FK1_APP_CLIENT_ID.getInverseKey());

        return _securityApp;
    }

    private transient SecurityAppRegAccessPath _securityAppRegAccess;

    /**
     * Get the implicit to-many join path to the
     * <code>security.security_app_reg_access</code> table
     */
    public SecurityAppRegAccessPath securityAppRegAccess() {
        if (_securityAppRegAccess == null)
            _securityAppRegAccess = new SecurityAppRegAccessPath(this, null, Keys.FK1_APP_REG_ACC_CLNT_ID.getInverseKey());

        return _securityAppRegAccess;
    }

    private transient SecurityAppRegFileAccessPath _securityAppRegFileAccess;

    /**
     * Get the implicit to-many join path to the
     * <code>security.security_app_reg_file_access</code> table
     */
    public SecurityAppRegFileAccessPath securityAppRegFileAccess() {
        if (_securityAppRegFileAccess == null)
            _securityAppRegFileAccess = new SecurityAppRegFileAccessPath(this, null, Keys.FK1_APP_REG_FILE_ACC_CLNT_ID.getInverseKey());

        return _securityAppRegFileAccess;
    }

    private transient SecurityAppRegPackagePath _securityAppRegPackage;

    /**
     * Get the implicit to-many join path to the
     * <code>security.security_app_reg_package</code> table
     */
    public SecurityAppRegPackagePath securityAppRegPackage() {
        if (_securityAppRegPackage == null)
            _securityAppRegPackage = new SecurityAppRegPackagePath(this, null, Keys.FK1_APP_REG_PKG_CLNT_ID.getInverseKey());

        return _securityAppRegPackage;
    }

    private transient SecurityAppRegUserRolePath _securityAppRegUserRole;

    /**
     * Get the implicit to-many join path to the
     * <code>security.security_app_reg_user_role</code> table
     */
    public SecurityAppRegUserRolePath securityAppRegUserRole() {
        if (_securityAppRegUserRole == null)
            _securityAppRegUserRole = new SecurityAppRegUserRolePath(this, null, Keys.FK1_APP_REG_ROLE_CLNT_ID.getInverseKey());

        return _securityAppRegUserRole;
    }

    private transient SecurityClientAddressPath _securityClientAddress;

    /**
     * Get the implicit to-many join path to the
     * <code>security.security_client_address</code> table
     */
    public SecurityClientAddressPath securityClientAddress() {
        if (_securityClientAddress == null)
            _securityClientAddress = new SecurityClientAddressPath(this, null, Keys.FK1_CLIENT_ADDRESS_CLIENT_ID.getInverseKey());

        return _securityClientAddress;
    }

    private transient SecurityClientHierarchyPath _fk1ClientHierarchyClientId;

    /**
     * Get the implicit to-many join path to the
     * <code>security.security_client_hierarchy</code> table, via the
     * <code>FK1_CLIENT_HIERARCHY_CLIENT_ID</code> key
     */
    public SecurityClientHierarchyPath fk1ClientHierarchyClientId() {
        if (_fk1ClientHierarchyClientId == null)
            _fk1ClientHierarchyClientId = new SecurityClientHierarchyPath(this, null, Keys.FK1_CLIENT_HIERARCHY_CLIENT_ID.getInverseKey());

        return _fk1ClientHierarchyClientId;
    }

    private transient SecurityClientHierarchyPath _fk1ClientHierarchyLevel_0;

    /**
     * Get the implicit to-many join path to the
     * <code>security.security_client_hierarchy</code> table, via the
     * <code>FK1_CLIENT_HIERARCHY_LEVEL_0</code> key
     */
    public SecurityClientHierarchyPath fk1ClientHierarchyLevel_0() {
        if (_fk1ClientHierarchyLevel_0 == null)
            _fk1ClientHierarchyLevel_0 = new SecurityClientHierarchyPath(this, null, Keys.FK1_CLIENT_HIERARCHY_LEVEL_0.getInverseKey());

        return _fk1ClientHierarchyLevel_0;
    }

    private transient SecurityClientHierarchyPath _fk1ClientHierarchyLevel_1;

    /**
     * Get the implicit to-many join path to the
     * <code>security.security_client_hierarchy</code> table, via the
     * <code>FK1_CLIENT_HIERARCHY_LEVEL_1</code> key
     */
    public SecurityClientHierarchyPath fk1ClientHierarchyLevel_1() {
        if (_fk1ClientHierarchyLevel_1 == null)
            _fk1ClientHierarchyLevel_1 = new SecurityClientHierarchyPath(this, null, Keys.FK1_CLIENT_HIERARCHY_LEVEL_1.getInverseKey());

        return _fk1ClientHierarchyLevel_1;
    }

    private transient SecurityClientHierarchyPath _fk1ClientHierarchyLevel_2;

    /**
     * Get the implicit to-many join path to the
     * <code>security.security_client_hierarchy</code> table, via the
     * <code>FK1_CLIENT_HIERARCHY_LEVEL_2</code> key
     */
    public SecurityClientHierarchyPath fk1ClientHierarchyLevel_2() {
        if (_fk1ClientHierarchyLevel_2 == null)
            _fk1ClientHierarchyLevel_2 = new SecurityClientHierarchyPath(this, null, Keys.FK1_CLIENT_HIERARCHY_LEVEL_2.getInverseKey());

        return _fk1ClientHierarchyLevel_2;
    }

    private transient SecurityClientHierarchyPath _fk1ClientHierarchyLevel_3;

    /**
     * Get the implicit to-many join path to the
     * <code>security.security_client_hierarchy</code> table, via the
     * <code>FK1_CLIENT_HIERARCHY_LEVEL_3</code> key
     */
    public SecurityClientHierarchyPath fk1ClientHierarchyLevel_3() {
        if (_fk1ClientHierarchyLevel_3 == null)
            _fk1ClientHierarchyLevel_3 = new SecurityClientHierarchyPath(this, null, Keys.FK1_CLIENT_HIERARCHY_LEVEL_3.getInverseKey());

        return _fk1ClientHierarchyLevel_3;
    }

    private transient SecurityClientOtpPolicyPath _securityClientOtpPolicy;

    /**
     * Get the implicit to-many join path to the
     * <code>security.security_client_otp_policy</code> table
     */
    public SecurityClientOtpPolicyPath securityClientOtpPolicy() {
        if (_securityClientOtpPolicy == null)
            _securityClientOtpPolicy = new SecurityClientOtpPolicyPath(this, null, Keys.FK1_CLIENT_OTP_POL_CLIENT_ID.getInverseKey());

        return _securityClientOtpPolicy;
    }

    private transient SecurityClientPackagePath _securityClientPackage;

    /**
     * Get the implicit to-many join path to the
     * <code>security.security_client_package</code> table
     */
    public SecurityClientPackagePath securityClientPackage() {
        if (_securityClientPackage == null)
            _securityClientPackage = new SecurityClientPackagePath(this, null, Keys.FK1_CLIENT_PACKAGE_CLIENT_ID.getInverseKey());

        return _securityClientPackage;
    }

    private transient SecurityClientPinPolicyPath _securityClientPinPolicy;

    /**
     * Get the implicit to-many join path to the
     * <code>security.security_client_pin_policy</code> table
     */
    public SecurityClientPinPolicyPath securityClientPinPolicy() {
        if (_securityClientPinPolicy == null)
            _securityClientPinPolicy = new SecurityClientPinPolicyPath(this, null, Keys.FK1_CLIENT_PIN_POL_CLIENT_ID.getInverseKey());

        return _securityClientPinPolicy;
    }

    private transient SecurityClientPasswordPolicyPath _securityClientPasswordPolicy;

    /**
     * Get the implicit to-many join path to the
     * <code>security.security_client_password_policy</code> table
     */
    public SecurityClientPasswordPolicyPath securityClientPasswordPolicy() {
        if (_securityClientPasswordPolicy == null)
            _securityClientPasswordPolicy = new SecurityClientPasswordPolicyPath(this, null, Keys.FK1_CLIENT_PWD_POL_CLIENT_ID.getInverseKey());

        return _securityClientPasswordPolicy;
    }

    private transient SecurityClientUrlPath _securityClientUrl;

    /**
     * Get the implicit to-many join path to the
     * <code>security.security_client_url</code> table
     */
    public SecurityClientUrlPath securityClientUrl() {
        if (_securityClientUrl == null)
            _securityClientUrl = new SecurityClientUrlPath(this, null, Keys.FK1_CLIENT_URL_CLIENT_ID.getInverseKey());

        return _securityClientUrl;
    }

    private transient SecurityOrgStructurePath _securityOrgStructure;

    /**
     * Get the implicit to-many join path to the
     * <code>security.security_org_structure</code> table
     */
    public SecurityOrgStructurePath securityOrgStructure() {
        if (_securityOrgStructure == null)
            _securityOrgStructure = new SecurityOrgStructurePath(this, null, Keys.FK1_ORG_STRUCTURE_CLIENT_ID.getInverseKey());

        return _securityOrgStructure;
    }

    private transient SecurityPackagePath _securityPackage;

    /**
     * Get the implicit to-many join path to the
     * <code>security.security_package</code> table
     */
    public SecurityPackagePath securityPackage() {
        if (_securityPackage == null)
            _securityPackage = new SecurityPackagePath(this, null, Keys.FK1_PACKAGE_CLIENT_ID.getInverseKey());

        return _securityPackage;
    }

    private transient SecurityPermissionPath _securityPermission;

    /**
     * Get the implicit to-many join path to the
     * <code>security.security_permission</code> table
     */
    public SecurityPermissionPath securityPermission() {
        if (_securityPermission == null)
            _securityPermission = new SecurityPermissionPath(this, null, Keys.FK1_PERMISSION_CLIENT_ID.getInverseKey());

        return _securityPermission;
    }

    private transient SecurityRolePath _securityRole;

    /**
     * Get the implicit to-many join path to the
     * <code>security.security_role</code> table
     */
    public SecurityRolePath securityRole() {
        if (_securityRole == null)
            _securityRole = new SecurityRolePath(this, null, Keys.FK1_ROLE_CLIENT_ID.getInverseKey());

        return _securityRole;
    }

    private transient SecurityUserPath _securityUser;

    /**
     * Get the implicit to-many join path to the
     * <code>security.security_user</code> table
     */
    public SecurityUserPath securityUser() {
        if (_securityUser == null)
            _securityUser = new SecurityUserPath(this, null, Keys.FK1_USER_CLIENT_ID.getInverseKey());

        return _securityUser;
    }

    private transient SecurityAppPropertyPath _securityAppProperty;

    /**
     * Get the implicit to-many join path to the
     * <code>security.security_app_property</code> table
     */
    public SecurityAppPropertyPath securityAppProperty() {
        if (_securityAppProperty == null)
            _securityAppProperty = new SecurityAppPropertyPath(this, null, Keys.FK2_APP_PROP_CLNT_ID.getInverseKey());

        return _securityAppProperty;
    }

    private transient SecurityAppRegIntegrationPath _securityAppRegIntegration;

    /**
     * Get the implicit to-many join path to the
     * <code>security.security_app_reg_integration</code> table
     */
    public SecurityAppRegIntegrationPath securityAppRegIntegration() {
        if (_securityAppRegIntegration == null)
            _securityAppRegIntegration = new SecurityAppRegIntegrationPath(this, null, Keys.FK2_APP_REG_INTEGRATION_CLIENT_ID.getInverseKey());

        return _securityAppRegIntegration;
    }

    /**
     * Get the implicit many-to-many join path to the
     * <code>security.security_app</code> table, via the
     * <code>FK1_APP_ACCESS_APP_ID</code> key
     */
    public SecurityAppPath fk1AppAccessAppId() {
        return securityAppAccess().securityApp();
    }

    /**
     * Get the implicit many-to-many join path to the
     * <code>security.security_app</code> table, via the
     * <code>FK2_CLIENT_OTP_POL_APP_ID</code> key
     */
    public SecurityAppPath fk2ClientOtpPolAppId() {
        return securityClientOtpPolicy().securityApp();
    }

    /**
     * Get the implicit many-to-many join path to the
     * <code>security.security_app</code> table, via the
     * <code>FK2_CLIENT_PWD_POL_APP_ID</code> key
     */
    public SecurityAppPath fk2ClientPwdPolAppId() {
        return securityClientPasswordPolicy().securityApp();
    }

    /**
     * Get the implicit many-to-many join path to the
     * <code>security.security_app</code> table, via the
     * <code>FK2_CLIENT_PIN_POL_APP_ID</code> key
     */
    public SecurityAppPath fk2ClientPinPolAppId() {
        return securityClientPinPolicy().securityApp();
    }

    @Override
    public SecurityClient as(String alias) {
        return new SecurityClient(DSL.name(alias), this);
    }

    @Override
    public SecurityClient as(Name alias) {
        return new SecurityClient(alias, this);
    }

    @Override
    public SecurityClient as(Table<?> alias) {
        return new SecurityClient(alias.getQualifiedName(), this);
    }

    /**
     * Rename this table
     */
    @Override
    public SecurityClient rename(String name) {
        return new SecurityClient(DSL.name(name), null);
    }

    /**
     * Rename this table
     */
    @Override
    public SecurityClient rename(Name name) {
        return new SecurityClient(name, null);
    }

    /**
     * Rename this table
     */
    @Override
    public SecurityClient rename(Table<?> name) {
        return new SecurityClient(name.getQualifiedName(), null);
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public SecurityClient where(Condition condition) {
        return new SecurityClient(getQualifiedName(), aliased() ? this : null, null, condition);
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public SecurityClient where(Collection<? extends Condition> conditions) {
        return where(DSL.and(conditions));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public SecurityClient where(Condition... conditions) {
        return where(DSL.and(conditions));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public SecurityClient where(Field<Boolean> condition) {
        return where(DSL.condition(condition));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    @PlainSQL
    public SecurityClient where(SQL condition) {
        return where(DSL.condition(condition));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    @PlainSQL
    public SecurityClient where(@Stringly.SQL String condition) {
        return where(DSL.condition(condition));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    @PlainSQL
    public SecurityClient where(@Stringly.SQL String condition, Object... binds) {
        return where(DSL.condition(condition, binds));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    @PlainSQL
    public SecurityClient where(@Stringly.SQL String condition, QueryPart... parts) {
        return where(DSL.condition(condition, parts));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public SecurityClient whereExists(Select<?> select) {
        return where(DSL.exists(select));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public SecurityClient whereNotExists(Select<?> select) {
        return where(DSL.notExists(select));
    }
}
