/*
 * This file is generated by jOOQ.
 */
package com.fincity.security.jooq.tables;


import com.fincity.security.jooq.Keys;
import com.fincity.security.jooq.Security;
import com.fincity.security.jooq.tables.SecurityApp.SecurityAppPath;
import com.fincity.security.jooq.tables.SecurityClient.SecurityClientPath;
import com.fincity.security.jooq.tables.records.SecurityClientPinPolicyRecord;

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
import org.jooq.types.UShort;


/**
 * This class is generated by jOOQ.
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes", "this-escape" })
public class SecurityClientPinPolicy extends TableImpl<SecurityClientPinPolicyRecord> {

    private static final long serialVersionUID = 1L;

    /**
     * The reference instance of
     * <code>security.security_client_pin_policy</code>
     */
    public static final SecurityClientPinPolicy SECURITY_CLIENT_PIN_POLICY = new SecurityClientPinPolicy();

    /**
     * The class holding records for this type
     */
    @Override
    public Class<SecurityClientPinPolicyRecord> getRecordType() {
        return SecurityClientPinPolicyRecord.class;
    }

    /**
     * The column <code>security.security_client_pin_policy.ID</code>. Primary
     * key, unique identifier for each PIN policy entry
     */
    public final TableField<SecurityClientPinPolicyRecord, ULong> ID = createField(DSL.name("ID"), SQLDataType.BIGINTUNSIGNED.nullable(false).identity(true), this, "Primary key, unique identifier for each PIN policy entry");

    /**
     * The column <code>security.security_client_pin_policy.CLIENT_ID</code>.
     * Identifier for the client to which this PIN policy belongs. References
     * security_client table
     */
    public final TableField<SecurityClientPinPolicyRecord, ULong> CLIENT_ID = createField(DSL.name("CLIENT_ID"), SQLDataType.BIGINTUNSIGNED.nullable(false), this, "Identifier for the client to which this PIN policy belongs. References security_client table");

    /**
     * The column <code>security.security_client_pin_policy.APP_ID</code>.
     * Identifier for the application to which this PIN policy belongs.
     * References security_app table
     */
    public final TableField<SecurityClientPinPolicyRecord, ULong> APP_ID = createField(DSL.name("APP_ID"), SQLDataType.BIGINTUNSIGNED.nullable(false), this, "Identifier for the application to which this PIN policy belongs. References security_app table");

    /**
     * The column <code>security.security_client_pin_policy.LENGTH</code>.
     * Length of the PIN to be generated
     */
    public final TableField<SecurityClientPinPolicyRecord, UShort> LENGTH = createField(DSL.name("LENGTH"), SQLDataType.SMALLINTUNSIGNED.nullable(false).defaultValue(DSL.inline("4", SQLDataType.SMALLINTUNSIGNED)), this, "Length of the PIN to be generated");

    /**
     * The column
     * <code>security.security_client_pin_policy.RE_LOGIN_AFTER_INTERVAL</code>.
     * Time interval in minutes after which re-login is required
     */
    public final TableField<SecurityClientPinPolicyRecord, ULong> RE_LOGIN_AFTER_INTERVAL = createField(DSL.name("RE_LOGIN_AFTER_INTERVAL"), SQLDataType.BIGINTUNSIGNED.nullable(false).defaultValue(DSL.inline("120", SQLDataType.BIGINTUNSIGNED)), this, "Time interval in minutes after which re-login is required");

    /**
     * The column
     * <code>security.security_client_pin_policy.EXPIRY_IN_DAYS</code>. Number
     * of days after which the PIN expires
     */
    public final TableField<SecurityClientPinPolicyRecord, UShort> EXPIRY_IN_DAYS = createField(DSL.name("EXPIRY_IN_DAYS"), SQLDataType.SMALLINTUNSIGNED.nullable(false).defaultValue(DSL.inline("30", SQLDataType.SMALLINTUNSIGNED)), this, "Number of days after which the PIN expires");

    /**
     * The column
     * <code>security.security_client_pin_policy.EXPIRY_WARN_IN_DAYS</code>.
     * Number of days before expiry to warn the user
     */
    public final TableField<SecurityClientPinPolicyRecord, UShort> EXPIRY_WARN_IN_DAYS = createField(DSL.name("EXPIRY_WARN_IN_DAYS"), SQLDataType.SMALLINTUNSIGNED.nullable(false).defaultValue(DSL.inline("25", SQLDataType.SMALLINTUNSIGNED)), this, "Number of days before expiry to warn the user");

    /**
     * The column
     * <code>security.security_client_pin_policy.PIN_HISTORY_COUNT</code>.
     * Remember how many pin
     */
    public final TableField<SecurityClientPinPolicyRecord, UShort> PIN_HISTORY_COUNT = createField(DSL.name("PIN_HISTORY_COUNT"), SQLDataType.SMALLINTUNSIGNED.nullable(false).defaultValue(DSL.inline("3", SQLDataType.SMALLINTUNSIGNED)), this, "Remember how many pin");

    /**
     * The column
     * <code>security.security_client_pin_policy.NO_FAILED_ATTEMPTS</code>.
     * Maximum number of failed attempts allowed before PIN login is blocked
     */
    public final TableField<SecurityClientPinPolicyRecord, UShort> NO_FAILED_ATTEMPTS = createField(DSL.name("NO_FAILED_ATTEMPTS"), SQLDataType.SMALLINTUNSIGNED.nullable(false).defaultValue(DSL.inline("3", SQLDataType.SMALLINTUNSIGNED)), this, "Maximum number of failed attempts allowed before PIN login is blocked");

    /**
     * The column
     * <code>security.security_client_pin_policy.USER_LOCK_TIME</code>. Time in
     * minutes for which user need to be locked it policy violates
     */
    public final TableField<SecurityClientPinPolicyRecord, ULong> USER_LOCK_TIME = createField(DSL.name("USER_LOCK_TIME"), SQLDataType.BIGINTUNSIGNED.nullable(false).defaultValue(DSL.inline("30", SQLDataType.BIGINTUNSIGNED)), this, "Time in minutes for which user need to be locked it policy violates");

    /**
     * The column <code>security.security_client_pin_policy.CREATED_BY</code>.
     * ID of the user who created this row
     */
    public final TableField<SecurityClientPinPolicyRecord, ULong> CREATED_BY = createField(DSL.name("CREATED_BY"), SQLDataType.BIGINTUNSIGNED, this, "ID of the user who created this row");

    /**
     * The column <code>security.security_client_pin_policy.CREATED_AT</code>.
     * Time when this row is created
     */
    public final TableField<SecurityClientPinPolicyRecord, LocalDateTime> CREATED_AT = createField(DSL.name("CREATED_AT"), SQLDataType.LOCALDATETIME(0).nullable(false).defaultValue(DSL.field(DSL.raw("CURRENT_TIMESTAMP"), SQLDataType.LOCALDATETIME)), this, "Time when this row is created");

    /**
     * The column <code>security.security_client_pin_policy.UPDATED_BY</code>.
     * ID of the user who last updated this row
     */
    public final TableField<SecurityClientPinPolicyRecord, ULong> UPDATED_BY = createField(DSL.name("UPDATED_BY"), SQLDataType.BIGINTUNSIGNED, this, "ID of the user who last updated this row");

    /**
     * The column <code>security.security_client_pin_policy.UPDATED_AT</code>.
     * Time when this row is last updated
     */
    public final TableField<SecurityClientPinPolicyRecord, LocalDateTime> UPDATED_AT = createField(DSL.name("UPDATED_AT"), SQLDataType.LOCALDATETIME(0).nullable(false).defaultValue(DSL.field(DSL.raw("CURRENT_TIMESTAMP"), SQLDataType.LOCALDATETIME)), this, "Time when this row is last updated");

    private SecurityClientPinPolicy(Name alias, Table<SecurityClientPinPolicyRecord> aliased) {
        this(alias, aliased, (Field<?>[]) null, null);
    }

    private SecurityClientPinPolicy(Name alias, Table<SecurityClientPinPolicyRecord> aliased, Field<?>[] parameters, Condition where) {
        super(alias, null, aliased, parameters, DSL.comment(""), TableOptions.table(), where);
    }

    /**
     * Create an aliased <code>security.security_client_pin_policy</code> table
     * reference
     */
    public SecurityClientPinPolicy(String alias) {
        this(DSL.name(alias), SECURITY_CLIENT_PIN_POLICY);
    }

    /**
     * Create an aliased <code>security.security_client_pin_policy</code> table
     * reference
     */
    public SecurityClientPinPolicy(Name alias) {
        this(alias, SECURITY_CLIENT_PIN_POLICY);
    }

    /**
     * Create a <code>security.security_client_pin_policy</code> table reference
     */
    public SecurityClientPinPolicy() {
        this(DSL.name("security_client_pin_policy"), null);
    }

    public <O extends Record> SecurityClientPinPolicy(Table<O> path, ForeignKey<O, SecurityClientPinPolicyRecord> childPath, InverseForeignKey<O, SecurityClientPinPolicyRecord> parentPath) {
        super(path, childPath, parentPath, SECURITY_CLIENT_PIN_POLICY);
    }

    /**
     * A subtype implementing {@link Path} for simplified path-based joins.
     */
    public static class SecurityClientPinPolicyPath extends SecurityClientPinPolicy implements Path<SecurityClientPinPolicyRecord> {

        private static final long serialVersionUID = 1L;
        public <O extends Record> SecurityClientPinPolicyPath(Table<O> path, ForeignKey<O, SecurityClientPinPolicyRecord> childPath, InverseForeignKey<O, SecurityClientPinPolicyRecord> parentPath) {
            super(path, childPath, parentPath);
        }
        private SecurityClientPinPolicyPath(Name alias, Table<SecurityClientPinPolicyRecord> aliased) {
            super(alias, aliased);
        }

        @Override
        public SecurityClientPinPolicyPath as(String alias) {
            return new SecurityClientPinPolicyPath(DSL.name(alias), this);
        }

        @Override
        public SecurityClientPinPolicyPath as(Name alias) {
            return new SecurityClientPinPolicyPath(alias, this);
        }

        @Override
        public SecurityClientPinPolicyPath as(Table<?> alias) {
            return new SecurityClientPinPolicyPath(alias.getQualifiedName(), this);
        }
    }

    @Override
    public Schema getSchema() {
        return aliased() ? null : Security.SECURITY;
    }

    @Override
    public Identity<SecurityClientPinPolicyRecord, ULong> getIdentity() {
        return (Identity<SecurityClientPinPolicyRecord, ULong>) super.getIdentity();
    }

    @Override
    public UniqueKey<SecurityClientPinPolicyRecord> getPrimaryKey() {
        return Keys.KEY_SECURITY_CLIENT_PIN_POLICY_PRIMARY;
    }

    @Override
    public List<UniqueKey<SecurityClientPinPolicyRecord>> getUniqueKeys() {
        return Arrays.asList(Keys.KEY_SECURITY_CLIENT_PIN_POLICY_UK1_CLIENT_PIN_POL_CLIENT_ID_APP_ID);
    }

    @Override
    public List<ForeignKey<SecurityClientPinPolicyRecord, ?>> getReferences() {
        return Arrays.asList(Keys.FK1_CLIENT_PIN_POL_CLIENT_ID, Keys.FK2_CLIENT_PIN_POL_APP_ID);
    }

    private transient SecurityClientPath _securityClient;

    /**
     * Get the implicit join path to the <code>security.security_client</code>
     * table.
     */
    public SecurityClientPath securityClient() {
        if (_securityClient == null)
            _securityClient = new SecurityClientPath(this, Keys.FK1_CLIENT_PIN_POL_CLIENT_ID, null);

        return _securityClient;
    }

    private transient SecurityAppPath _securityApp;

    /**
     * Get the implicit join path to the <code>security.security_app</code>
     * table.
     */
    public SecurityAppPath securityApp() {
        if (_securityApp == null)
            _securityApp = new SecurityAppPath(this, Keys.FK2_CLIENT_PIN_POL_APP_ID, null);

        return _securityApp;
    }

    @Override
    public SecurityClientPinPolicy as(String alias) {
        return new SecurityClientPinPolicy(DSL.name(alias), this);
    }

    @Override
    public SecurityClientPinPolicy as(Name alias) {
        return new SecurityClientPinPolicy(alias, this);
    }

    @Override
    public SecurityClientPinPolicy as(Table<?> alias) {
        return new SecurityClientPinPolicy(alias.getQualifiedName(), this);
    }

    /**
     * Rename this table
     */
    @Override
    public SecurityClientPinPolicy rename(String name) {
        return new SecurityClientPinPolicy(DSL.name(name), null);
    }

    /**
     * Rename this table
     */
    @Override
    public SecurityClientPinPolicy rename(Name name) {
        return new SecurityClientPinPolicy(name, null);
    }

    /**
     * Rename this table
     */
    @Override
    public SecurityClientPinPolicy rename(Table<?> name) {
        return new SecurityClientPinPolicy(name.getQualifiedName(), null);
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public SecurityClientPinPolicy where(Condition condition) {
        return new SecurityClientPinPolicy(getQualifiedName(), aliased() ? this : null, null, condition);
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public SecurityClientPinPolicy where(Collection<? extends Condition> conditions) {
        return where(DSL.and(conditions));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public SecurityClientPinPolicy where(Condition... conditions) {
        return where(DSL.and(conditions));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public SecurityClientPinPolicy where(Field<Boolean> condition) {
        return where(DSL.condition(condition));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    @PlainSQL
    public SecurityClientPinPolicy where(SQL condition) {
        return where(DSL.condition(condition));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    @PlainSQL
    public SecurityClientPinPolicy where(@Stringly.SQL String condition) {
        return where(DSL.condition(condition));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    @PlainSQL
    public SecurityClientPinPolicy where(@Stringly.SQL String condition, Object... binds) {
        return where(DSL.condition(condition, binds));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    @PlainSQL
    public SecurityClientPinPolicy where(@Stringly.SQL String condition, QueryPart... parts) {
        return where(DSL.condition(condition, parts));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public SecurityClientPinPolicy whereExists(Select<?> select) {
        return where(DSL.exists(select));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public SecurityClientPinPolicy whereNotExists(Select<?> select) {
        return where(DSL.notExists(select));
    }
}
