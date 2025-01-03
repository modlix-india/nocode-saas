/*
 * This file is generated by jOOQ.
 */
package com.fincity.security.jooq.tables;


import com.fincity.security.jooq.Indexes;
import com.fincity.security.jooq.Keys;
import com.fincity.security.jooq.Security;
import com.fincity.security.jooq.enums.SecurityOtpTargetType;
import com.fincity.security.jooq.tables.SecurityApp.SecurityAppPath;
import com.fincity.security.jooq.tables.SecurityUser.SecurityUserPath;
import com.fincity.security.jooq.tables.records.SecurityOtpRecord;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

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
public class SecurityOtp extends TableImpl<SecurityOtpRecord> {

    private static final long serialVersionUID = 1L;

    /**
     * The reference instance of <code>security.security_otp</code>
     */
    public static final SecurityOtp SECURITY_OTP = new SecurityOtp();

    /**
     * The class holding records for this type
     */
    @Override
    public Class<SecurityOtpRecord> getRecordType() {
        return SecurityOtpRecord.class;
    }

    /**
     * The column <code>security.security_otp.ID</code>. Primary key, unique
     * identifier for each OTP entry
     */
    public final TableField<SecurityOtpRecord, ULong> ID = createField(DSL.name("ID"), SQLDataType.BIGINTUNSIGNED.nullable(false).identity(true), this, "Primary key, unique identifier for each OTP entry");

    /**
     * The column <code>security.security_otp.APP_ID</code>. Identifier for the
     * application to which this OTP belongs. References security_app table
     */
    public final TableField<SecurityOtpRecord, ULong> APP_ID = createField(DSL.name("APP_ID"), SQLDataType.BIGINTUNSIGNED.nullable(false), this, "Identifier for the application to which this OTP belongs. References security_app table");

    /**
     * The column <code>security.security_otp.USER_ID</code>. Identifier for the
     * user for whom this OTP is generated. References security_user table
     */
    public final TableField<SecurityOtpRecord, ULong> USER_ID = createField(DSL.name("USER_ID"), SQLDataType.BIGINTUNSIGNED.nullable(false), this, "Identifier for the user for whom this OTP is generated. References security_user table");

    /**
     * The column <code>security.security_otp.EMAIL_ID</code>. Email ID to which
     * otp was sent
     */
    public final TableField<SecurityOtpRecord, String> EMAIL_ID = createField(DSL.name("EMAIL_ID"), SQLDataType.VARCHAR(320), this, "Email ID to which otp was sent");

    /**
     * The column <code>security.security_otp.PHONE_NUMBER</code>. Phone Number
     * to which otp was sent
     */
    public final TableField<SecurityOtpRecord, String> PHONE_NUMBER = createField(DSL.name("PHONE_NUMBER"), SQLDataType.CHAR(32), this, "Phone Number to which otp was sent");

    /**
     * The column <code>security.security_otp.PURPOSE</code>. Purpose or reason
     * for the OTP (e.g., authentication, password reset, etc.)
     */
    public final TableField<SecurityOtpRecord, String> PURPOSE = createField(DSL.name("PURPOSE"), SQLDataType.VARCHAR(255).nullable(false), this, "Purpose or reason for the OTP (e.g., authentication, password reset, etc.)");

    /**
     * The column <code>security.security_otp.TARGET_TYPE</code>. The target
     * medium for the OTP delivery: EMAIL, PHONE, or BOTH
     */
    public final TableField<SecurityOtpRecord, SecurityOtpTargetType> TARGET_TYPE = createField(DSL.name("TARGET_TYPE"), SQLDataType.VARCHAR(5).nullable(false).defaultValue(DSL.inline("EMAIL", SQLDataType.VARCHAR)).asEnumDataType(SecurityOtpTargetType.class), this, "The target medium for the OTP delivery: EMAIL, PHONE, or BOTH");

    /**
     * The column <code>security.security_otp.UNIQUE_CODE</code>. The hashed OTP
     * code used for verification
     */
    public final TableField<SecurityOtpRecord, String> UNIQUE_CODE = createField(DSL.name("UNIQUE_CODE"), SQLDataType.CHAR(60).nullable(false), this, "The hashed OTP code used for verification");

    /**
     * The column <code>security.security_otp.EXPIRES_AT</code>. Timestamp
     * indicating when the OTP expires and becomes invalid
     */
    public final TableField<SecurityOtpRecord, LocalDateTime> EXPIRES_AT = createField(DSL.name("EXPIRES_AT"), SQLDataType.LOCALDATETIME(0).nullable(false), this, "Timestamp indicating when the OTP expires and becomes invalid");

    /**
     * The column <code>security.security_otp.IP_ADDRESS</code>. IP address of
     * the user to track OTP generation or use, supports both IPv4 and IPv6
     */
    public final TableField<SecurityOtpRecord, String> IP_ADDRESS = createField(DSL.name("IP_ADDRESS"), SQLDataType.CHAR(45), this, "IP address of the user to track OTP generation or use, supports both IPv4 and IPv6");

    /**
     * The column <code>security.security_otp.CREATED_BY</code>.
     */
    public final TableField<SecurityOtpRecord, ULong> CREATED_BY = createField(DSL.name("CREATED_BY"), SQLDataType.BIGINTUNSIGNED, this, "");

    /**
     * The column <code>security.security_otp.CREATED_AT</code>.
     */
    public final TableField<SecurityOtpRecord, LocalDateTime> CREATED_AT = createField(DSL.name("CREATED_AT"), SQLDataType.LOCALDATETIME(0).nullable(false).defaultValue(DSL.field(DSL.raw("CURRENT_TIMESTAMP"), SQLDataType.LOCALDATETIME)), this, "");

    private SecurityOtp(Name alias, Table<SecurityOtpRecord> aliased) {
        this(alias, aliased, (Field<?>[]) null, null);
    }

    private SecurityOtp(Name alias, Table<SecurityOtpRecord> aliased, Field<?>[] parameters, Condition where) {
        super(alias, null, aliased, parameters, DSL.comment(""), TableOptions.table(), where);
    }

    /**
     * Create an aliased <code>security.security_otp</code> table reference
     */
    public SecurityOtp(String alias) {
        this(DSL.name(alias), SECURITY_OTP);
    }

    /**
     * Create an aliased <code>security.security_otp</code> table reference
     */
    public SecurityOtp(Name alias) {
        this(alias, SECURITY_OTP);
    }

    /**
     * Create a <code>security.security_otp</code> table reference
     */
    public SecurityOtp() {
        this(DSL.name("security_otp"), null);
    }

    public <O extends Record> SecurityOtp(Table<O> path, ForeignKey<O, SecurityOtpRecord> childPath, InverseForeignKey<O, SecurityOtpRecord> parentPath) {
        super(path, childPath, parentPath, SECURITY_OTP);
    }

    /**
     * A subtype implementing {@link Path} for simplified path-based joins.
     */
    public static class SecurityOtpPath extends SecurityOtp implements Path<SecurityOtpRecord> {

        private static final long serialVersionUID = 1L;
        public <O extends Record> SecurityOtpPath(Table<O> path, ForeignKey<O, SecurityOtpRecord> childPath, InverseForeignKey<O, SecurityOtpRecord> parentPath) {
            super(path, childPath, parentPath);
        }
        private SecurityOtpPath(Name alias, Table<SecurityOtpRecord> aliased) {
            super(alias, aliased);
        }

        @Override
        public SecurityOtpPath as(String alias) {
            return new SecurityOtpPath(DSL.name(alias), this);
        }

        @Override
        public SecurityOtpPath as(Name alias) {
            return new SecurityOtpPath(alias, this);
        }

        @Override
        public SecurityOtpPath as(Table<?> alias) {
            return new SecurityOtpPath(alias.getQualifiedName(), this);
        }
    }

    @Override
    public Schema getSchema() {
        return aliased() ? null : Security.SECURITY;
    }

    @Override
    public List<Index> getIndexes() {
        return Arrays.asList(Indexes.SECURITY_OTP_APP_ID, Indexes.SECURITY_OTP_APP_ID_2, Indexes.SECURITY_OTP_CREATED_AT, Indexes.SECURITY_OTP_EXPIRES_AT);
    }

    @Override
    public Identity<SecurityOtpRecord, ULong> getIdentity() {
        return (Identity<SecurityOtpRecord, ULong>) super.getIdentity();
    }

    @Override
    public UniqueKey<SecurityOtpRecord> getPrimaryKey() {
        return Keys.KEY_SECURITY_OTP_PRIMARY;
    }

    @Override
    public List<ForeignKey<SecurityOtpRecord, ?>> getReferences() {
        return Arrays.asList(Keys.FK1_OTP_APP_ID, Keys.FK2_OTP_USER_ID);
    }

    private transient SecurityAppPath _securityApp;

    /**
     * Get the implicit join path to the <code>security.security_app</code>
     * table.
     */
    public SecurityAppPath securityApp() {
        if (_securityApp == null)
            _securityApp = new SecurityAppPath(this, Keys.FK1_OTP_APP_ID, null);

        return _securityApp;
    }

    private transient SecurityUserPath _securityUser;

    /**
     * Get the implicit join path to the <code>security.security_user</code>
     * table.
     */
    public SecurityUserPath securityUser() {
        if (_securityUser == null)
            _securityUser = new SecurityUserPath(this, Keys.FK2_OTP_USER_ID, null);

        return _securityUser;
    }

    @Override
    public SecurityOtp as(String alias) {
        return new SecurityOtp(DSL.name(alias), this);
    }

    @Override
    public SecurityOtp as(Name alias) {
        return new SecurityOtp(alias, this);
    }

    @Override
    public SecurityOtp as(Table<?> alias) {
        return new SecurityOtp(alias.getQualifiedName(), this);
    }

    /**
     * Rename this table
     */
    @Override
    public SecurityOtp rename(String name) {
        return new SecurityOtp(DSL.name(name), null);
    }

    /**
     * Rename this table
     */
    @Override
    public SecurityOtp rename(Name name) {
        return new SecurityOtp(name, null);
    }

    /**
     * Rename this table
     */
    @Override
    public SecurityOtp rename(Table<?> name) {
        return new SecurityOtp(name.getQualifiedName(), null);
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public SecurityOtp where(Condition condition) {
        return new SecurityOtp(getQualifiedName(), aliased() ? this : null, null, condition);
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public SecurityOtp where(Collection<? extends Condition> conditions) {
        return where(DSL.and(conditions));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public SecurityOtp where(Condition... conditions) {
        return where(DSL.and(conditions));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public SecurityOtp where(Field<Boolean> condition) {
        return where(DSL.condition(condition));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    @PlainSQL
    public SecurityOtp where(SQL condition) {
        return where(DSL.condition(condition));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    @PlainSQL
    public SecurityOtp where(@Stringly.SQL String condition) {
        return where(DSL.condition(condition));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    @PlainSQL
    public SecurityOtp where(@Stringly.SQL String condition, Object... binds) {
        return where(DSL.condition(condition, binds));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    @PlainSQL
    public SecurityOtp where(@Stringly.SQL String condition, QueryPart... parts) {
        return where(DSL.condition(condition, parts));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public SecurityOtp whereExists(Select<?> select) {
        return where(DSL.exists(select));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public SecurityOtp whereNotExists(Select<?> select) {
        return where(DSL.notExists(select));
    }
}
