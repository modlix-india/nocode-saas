/*
 * This file is generated by jOOQ.
 */
package com.fincity.security.jooq.tables;


import com.fincity.saas.commons.jooq.convertor.JSONMysqlMapConvertor;
import com.fincity.security.jooq.Indexes;
import com.fincity.security.jooq.Keys;
import com.fincity.security.jooq.Security;
import com.fincity.security.jooq.tables.SecurityAppRegIntegration.SecurityAppRegIntegrationPath;
import com.fincity.security.jooq.tables.records.SecurityAppRegIntegrationTokensRecord;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

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
import org.jooq.types.ULong;


/**
 * This class is generated by jOOQ.
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes", "this-escape" })
public class SecurityAppRegIntegrationTokens extends TableImpl<SecurityAppRegIntegrationTokensRecord> {

    private static final long serialVersionUID = 1L;

    /**
     * The reference instance of
     * <code>security.security_app_reg_integration_tokens</code>
     */
    public static final SecurityAppRegIntegrationTokens SECURITY_APP_REG_INTEGRATION_TOKENS = new SecurityAppRegIntegrationTokens();

    /**
     * The class holding records for this type
     */
    @Override
    public Class<SecurityAppRegIntegrationTokensRecord> getRecordType() {
        return SecurityAppRegIntegrationTokensRecord.class;
    }

    /**
     * The column <code>security.security_app_reg_integration_tokens.ID</code>.
     * Primary key
     */
    public final TableField<SecurityAppRegIntegrationTokensRecord, ULong> ID = createField(DSL.name("ID"), SQLDataType.BIGINTUNSIGNED.nullable(false).identity(true), this, "Primary key");

    /**
     * The column
     * <code>security.security_app_reg_integration_tokens.INTEGRATION_ID</code>.
     * Integration ID
     */
    public final TableField<SecurityAppRegIntegrationTokensRecord, ULong> INTEGRATION_ID = createField(DSL.name("INTEGRATION_ID"), SQLDataType.BIGINTUNSIGNED.nullable(false), this, "Integration ID");

    /**
     * The column
     * <code>security.security_app_reg_integration_tokens.AUTH_CODE</code>. User
     * Consent Auth Code
     */
    public final TableField<SecurityAppRegIntegrationTokensRecord, String> AUTH_CODE = createField(DSL.name("AUTH_CODE"), SQLDataType.VARCHAR(512), this, "User Consent Auth Code");

    /**
     * The column
     * <code>security.security_app_reg_integration_tokens.STATE</code>. Session
     * id for login
     */
    public final TableField<SecurityAppRegIntegrationTokensRecord, String> STATE = createField(DSL.name("STATE"), SQLDataType.CHAR(64), this, "Session id for login");

    /**
     * The column
     * <code>security.security_app_reg_integration_tokens.REQUEST_PARAM</code>.
     * app metadata from request url
     */
    public final TableField<SecurityAppRegIntegrationTokensRecord, Map> REQUEST_PARAM = createField(DSL.name("REQUEST_PARAM"), SQLDataType.JSON, this, "app metadata from request url", new JSONMysqlMapConvertor());

    /**
     * The column
     * <code>security.security_app_reg_integration_tokens.TOKEN</code>. Token
     */
    public final TableField<SecurityAppRegIntegrationTokensRecord, String> TOKEN = createField(DSL.name("TOKEN"), SQLDataType.VARCHAR(512), this, "Token");

    /**
     * The column
     * <code>security.security_app_reg_integration_tokens.REFRESH_TOKEN</code>.
     * Refresh Token
     */
    public final TableField<SecurityAppRegIntegrationTokensRecord, String> REFRESH_TOKEN = createField(DSL.name("REFRESH_TOKEN"), SQLDataType.VARCHAR(512), this, "Refresh Token");

    /**
     * The column
     * <code>security.security_app_reg_integration_tokens.EXPIRES_AT</code>.
     * Token expiration time
     */
    public final TableField<SecurityAppRegIntegrationTokensRecord, LocalDateTime> EXPIRES_AT = createField(DSL.name("EXPIRES_AT"), SQLDataType.LOCALDATETIME(0), this, "Token expiration time");

    /**
     * The column
     * <code>security.security_app_reg_integration_tokens.TOKEN_METADATA</code>.
     * Token metadata
     */
    public final TableField<SecurityAppRegIntegrationTokensRecord, Map> TOKEN_METADATA = createField(DSL.name("TOKEN_METADATA"), SQLDataType.JSON, this, "Token metadata", new JSONMysqlMapConvertor());

    /**
     * The column
     * <code>security.security_app_reg_integration_tokens.USERNAME</code>.
     * Username
     */
    public final TableField<SecurityAppRegIntegrationTokensRecord, String> USERNAME = createField(DSL.name("USERNAME"), SQLDataType.VARCHAR(320), this, "Username");

    /**
     * The column
     * <code>security.security_app_reg_integration_tokens.USER_METADATA</code>.
     * User metadata
     */
    public final TableField<SecurityAppRegIntegrationTokensRecord, Map> USER_METADATA = createField(DSL.name("USER_METADATA"), SQLDataType.JSON, this, "User metadata", new JSONMysqlMapConvertor());

    /**
     * The column
     * <code>security.security_app_reg_integration_tokens.CREATED_AT</code>.
     * Time when this row is created
     */
    public final TableField<SecurityAppRegIntegrationTokensRecord, LocalDateTime> CREATED_AT = createField(DSL.name("CREATED_AT"), SQLDataType.LOCALDATETIME(0).nullable(false).defaultValue(DSL.field(DSL.raw("CURRENT_TIMESTAMP"), SQLDataType.LOCALDATETIME)), this, "Time when this row is created");

    /**
     * The column
     * <code>security.security_app_reg_integration_tokens.CREATED_BY</code>. ID
     * of the user who created this row
     */
    public final TableField<SecurityAppRegIntegrationTokensRecord, ULong> CREATED_BY = createField(DSL.name("CREATED_BY"), SQLDataType.BIGINTUNSIGNED, this, "ID of the user who created this row");

    /**
     * The column
     * <code>security.security_app_reg_integration_tokens.UPDATED_BY</code>. ID
     * of the user who updated this row
     */
    public final TableField<SecurityAppRegIntegrationTokensRecord, ULong> UPDATED_BY = createField(DSL.name("UPDATED_BY"), SQLDataType.BIGINTUNSIGNED, this, "ID of the user who updated this row");

    /**
     * The column
     * <code>security.security_app_reg_integration_tokens.UPDATED_AT</code>.
     * Time when this row is updated
     */
    public final TableField<SecurityAppRegIntegrationTokensRecord, LocalDateTime> UPDATED_AT = createField(DSL.name("UPDATED_AT"), SQLDataType.LOCALDATETIME(0).nullable(false).defaultValue(DSL.field(DSL.raw("CURRENT_TIMESTAMP"), SQLDataType.LOCALDATETIME)), this, "Time when this row is updated");

    private SecurityAppRegIntegrationTokens(Name alias, Table<SecurityAppRegIntegrationTokensRecord> aliased) {
        this(alias, aliased, (Field<?>[]) null, null);
    }

    private SecurityAppRegIntegrationTokens(Name alias, Table<SecurityAppRegIntegrationTokensRecord> aliased, Field<?>[] parameters, Condition where) {
        super(alias, null, aliased, parameters, DSL.comment(""), TableOptions.table(), where);
    }

    /**
     * Create an aliased
     * <code>security.security_app_reg_integration_tokens</code> table reference
     */
    public SecurityAppRegIntegrationTokens(String alias) {
        this(DSL.name(alias), SECURITY_APP_REG_INTEGRATION_TOKENS);
    }

    /**
     * Create an aliased
     * <code>security.security_app_reg_integration_tokens</code> table reference
     */
    public SecurityAppRegIntegrationTokens(Name alias) {
        this(alias, SECURITY_APP_REG_INTEGRATION_TOKENS);
    }

    /**
     * Create a <code>security.security_app_reg_integration_tokens</code> table
     * reference
     */
    public SecurityAppRegIntegrationTokens() {
        this(DSL.name("security_app_reg_integration_tokens"), null);
    }

    public <O extends Record> SecurityAppRegIntegrationTokens(Table<O> path, ForeignKey<O, SecurityAppRegIntegrationTokensRecord> childPath, InverseForeignKey<O, SecurityAppRegIntegrationTokensRecord> parentPath) {
        super(path, childPath, parentPath, SECURITY_APP_REG_INTEGRATION_TOKENS);
    }

    /**
     * A subtype implementing {@link Path} for simplified path-based joins.
     */
    public static class SecurityAppRegIntegrationTokensPath extends SecurityAppRegIntegrationTokens implements Path<SecurityAppRegIntegrationTokensRecord> {

        private static final long serialVersionUID = 1L;
        public <O extends Record> SecurityAppRegIntegrationTokensPath(Table<O> path, ForeignKey<O, SecurityAppRegIntegrationTokensRecord> childPath, InverseForeignKey<O, SecurityAppRegIntegrationTokensRecord> parentPath) {
            super(path, childPath, parentPath);
        }
        private SecurityAppRegIntegrationTokensPath(Name alias, Table<SecurityAppRegIntegrationTokensRecord> aliased) {
            super(alias, aliased);
        }

        @Override
        public SecurityAppRegIntegrationTokensPath as(String alias) {
            return new SecurityAppRegIntegrationTokensPath(DSL.name(alias), this);
        }

        @Override
        public SecurityAppRegIntegrationTokensPath as(Name alias) {
            return new SecurityAppRegIntegrationTokensPath(alias, this);
        }

        @Override
        public SecurityAppRegIntegrationTokensPath as(Table<?> alias) {
            return new SecurityAppRegIntegrationTokensPath(alias.getQualifiedName(), this);
        }
    }

    @Override
    public Schema getSchema() {
        return aliased() ? null : Security.SECURITY;
    }

    @Override
    public List<Index> getIndexes() {
        return Arrays.asList(Indexes.SECURITY_APP_REG_INTEGRATION_TOKENS_STATE);
    }

    @Override
    public Identity<SecurityAppRegIntegrationTokensRecord, ULong> getIdentity() {
        return (Identity<SecurityAppRegIntegrationTokensRecord, ULong>) super.getIdentity();
    }

    @Override
    public UniqueKey<SecurityAppRegIntegrationTokensRecord> getPrimaryKey() {
        return Keys.KEY_SECURITY_APP_REG_INTEGRATION_TOKENS_PRIMARY;
    }

    @Override
    public List<UniqueKey<SecurityAppRegIntegrationTokensRecord>> getUniqueKeys() {
        return Arrays.asList(Keys.KEY_SECURITY_APP_REG_INTEGRATION_TOKENS_UK1_INTEGRATION_TOKEN, Keys.KEY_SECURITY_APP_REG_INTEGRATION_TOKENS_UK2_INTEGRATION_TOKEN_STATE);
    }

    @Override
    public List<ForeignKey<SecurityAppRegIntegrationTokensRecord, ?>> getReferences() {
        return Arrays.asList(Keys.FK1_INTEGRATION_TOKEN_APP_REG_INTEGRATION_ID);
    }

    private transient SecurityAppRegIntegrationPath _securityAppRegIntegration;

    /**
     * Get the implicit join path to the
     * <code>security.security_app_reg_integration</code> table.
     */
    public SecurityAppRegIntegrationPath securityAppRegIntegration() {
        if (_securityAppRegIntegration == null)
            _securityAppRegIntegration = new SecurityAppRegIntegrationPath(this, Keys.FK1_INTEGRATION_TOKEN_APP_REG_INTEGRATION_ID, null);

        return _securityAppRegIntegration;
    }

    @Override
    public SecurityAppRegIntegrationTokens as(String alias) {
        return new SecurityAppRegIntegrationTokens(DSL.name(alias), this);
    }

    @Override
    public SecurityAppRegIntegrationTokens as(Name alias) {
        return new SecurityAppRegIntegrationTokens(alias, this);
    }

    @Override
    public SecurityAppRegIntegrationTokens as(Table<?> alias) {
        return new SecurityAppRegIntegrationTokens(alias.getQualifiedName(), this);
    }

    /**
     * Rename this table
     */
    @Override
    public SecurityAppRegIntegrationTokens rename(String name) {
        return new SecurityAppRegIntegrationTokens(DSL.name(name), null);
    }

    /**
     * Rename this table
     */
    @Override
    public SecurityAppRegIntegrationTokens rename(Name name) {
        return new SecurityAppRegIntegrationTokens(name, null);
    }

    /**
     * Rename this table
     */
    @Override
    public SecurityAppRegIntegrationTokens rename(Table<?> name) {
        return new SecurityAppRegIntegrationTokens(name.getQualifiedName(), null);
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public SecurityAppRegIntegrationTokens where(Condition condition) {
        return new SecurityAppRegIntegrationTokens(getQualifiedName(), aliased() ? this : null, null, condition);
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public SecurityAppRegIntegrationTokens where(Collection<? extends Condition> conditions) {
        return where(DSL.and(conditions));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public SecurityAppRegIntegrationTokens where(Condition... conditions) {
        return where(DSL.and(conditions));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public SecurityAppRegIntegrationTokens where(Field<Boolean> condition) {
        return where(DSL.condition(condition));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    @PlainSQL
    public SecurityAppRegIntegrationTokens where(SQL condition) {
        return where(DSL.condition(condition));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    @PlainSQL
    public SecurityAppRegIntegrationTokens where(@Stringly.SQL String condition) {
        return where(DSL.condition(condition));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    @PlainSQL
    public SecurityAppRegIntegrationTokens where(@Stringly.SQL String condition, Object... binds) {
        return where(DSL.condition(condition, binds));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    @PlainSQL
    public SecurityAppRegIntegrationTokens where(@Stringly.SQL String condition, QueryPart... parts) {
        return where(DSL.condition(condition, parts));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public SecurityAppRegIntegrationTokens whereExists(Select<?> select) {
        return where(DSL.exists(select));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public SecurityAppRegIntegrationTokens whereNotExists(Select<?> select) {
        return where(DSL.notExists(select));
    }
}
