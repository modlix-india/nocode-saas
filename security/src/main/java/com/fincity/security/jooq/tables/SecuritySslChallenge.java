/*
 * This file is generated by jOOQ.
 */
package com.fincity.security.jooq.tables;


import com.fincity.security.jooq.Keys;
import com.fincity.security.jooq.Security;
import com.fincity.security.jooq.tables.SecuritySslRequest.SecuritySslRequestPath;
import com.fincity.security.jooq.tables.records.SecuritySslChallengeRecord;

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
public class SecuritySslChallenge extends TableImpl<SecuritySslChallengeRecord> {

    private static final long serialVersionUID = 1L;

    /**
     * The reference instance of <code>security.security_ssl_challenge</code>
     */
    public static final SecuritySslChallenge SECURITY_SSL_CHALLENGE = new SecuritySslChallenge();

    /**
     * The class holding records for this type
     */
    @Override
    public Class<SecuritySslChallengeRecord> getRecordType() {
        return SecuritySslChallengeRecord.class;
    }

    /**
     * The column <code>security.security_ssl_challenge.ID</code>. Primary key
     */
    public final TableField<SecuritySslChallengeRecord, ULong> ID = createField(DSL.name("ID"), SQLDataType.BIGINTUNSIGNED.nullable(false).identity(true), this, "Primary key");

    /**
     * The column <code>security.security_ssl_challenge.REQUEST_ID</code>. SSL
     * request ID for which this challenge belongs to
     */
    public final TableField<SecuritySslChallengeRecord, ULong> REQUEST_ID = createField(DSL.name("REQUEST_ID"), SQLDataType.BIGINTUNSIGNED.nullable(false), this, "SSL request ID for which this challenge belongs to");

    /**
     * The column <code>security.security_ssl_challenge.CHALLENGE_TYPE</code>.
     * Challenge type
     */
    public final TableField<SecuritySslChallengeRecord, String> CHALLENGE_TYPE = createField(DSL.name("CHALLENGE_TYPE"), SQLDataType.VARCHAR(32).nullable(false), this, "Challenge type");

    /**
     * The column <code>security.security_ssl_challenge.DOMAIN</code>. Domain
     * for which this challenge is valid
     */
    public final TableField<SecuritySslChallengeRecord, String> DOMAIN = createField(DSL.name("DOMAIN"), SQLDataType.VARCHAR(1024).nullable(false), this, "Domain for which this challenge is valid");

    /**
     * The column <code>security.security_ssl_challenge.TOKEN</code>. Challenge
     * token for HTTP-01 challenge/Challenge TXT record name for DNS-01
     * challenge
     */
    public final TableField<SecuritySslChallengeRecord, String> TOKEN = createField(DSL.name("TOKEN"), SQLDataType.VARCHAR(1024).nullable(false), this, "Challenge token for HTTP-01 challenge/Challenge TXT record name for DNS-01 challenge");

    /**
     * The column <code>security.security_ssl_challenge.AUTHORIZATION</code>.
     * Challenge key authorization for HTTP-01 challenge/Digest for DNS-01
     * challenge
     */
    public final TableField<SecuritySslChallengeRecord, String> AUTHORIZATION = createField(DSL.name("AUTHORIZATION"), SQLDataType.VARCHAR(1024).nullable(false), this, "Challenge key authorization for HTTP-01 challenge/Digest for DNS-01 challenge");

    /**
     * The column <code>security.security_ssl_challenge.STATUS</code>. Challenge
     * status
     */
    public final TableField<SecuritySslChallengeRecord, String> STATUS = createField(DSL.name("STATUS"), SQLDataType.VARCHAR(128).nullable(false).defaultValue(DSL.inline("PENDING", SQLDataType.VARCHAR)), this, "Challenge status");

    /**
     * The column <code>security.security_ssl_challenge.FAILED_REASON</code>.
     * Reason for challenge failure
     */
    public final TableField<SecuritySslChallengeRecord, String> FAILED_REASON = createField(DSL.name("FAILED_REASON"), SQLDataType.CLOB, this, "Reason for challenge failure");

    /**
     * The column
     * <code>security.security_ssl_challenge.LAST_VALIDATED_AT</code>. Time when
     * this challenge is validated
     */
    public final TableField<SecuritySslChallengeRecord, LocalDateTime> LAST_VALIDATED_AT = createField(DSL.name("LAST_VALIDATED_AT"), SQLDataType.LOCALDATETIME(0), this, "Time when this challenge is validated");

    /**
     * The column <code>security.security_ssl_challenge.RETRY_COUNT</code>.
     * Number of times this challenge is retried
     */
    public final TableField<SecuritySslChallengeRecord, UInteger> RETRY_COUNT = createField(DSL.name("RETRY_COUNT"), SQLDataType.INTEGERUNSIGNED.nullable(false).defaultValue(DSL.inline("0", SQLDataType.INTEGERUNSIGNED)), this, "Number of times this challenge is retried");

    /**
     * The column <code>security.security_ssl_challenge.CREATED_BY</code>. ID of
     * the user who created this row
     */
    public final TableField<SecuritySslChallengeRecord, ULong> CREATED_BY = createField(DSL.name("CREATED_BY"), SQLDataType.BIGINTUNSIGNED, this, "ID of the user who created this row");

    /**
     * The column <code>security.security_ssl_challenge.CREATED_AT</code>. Time
     * when this row is created
     */
    public final TableField<SecuritySslChallengeRecord, LocalDateTime> CREATED_AT = createField(DSL.name("CREATED_AT"), SQLDataType.LOCALDATETIME(0).nullable(false).defaultValue(DSL.field(DSL.raw("CURRENT_TIMESTAMP"), SQLDataType.LOCALDATETIME)), this, "Time when this row is created");

    /**
     * The column <code>security.security_ssl_challenge.UPDATED_BY</code>. ID of
     * the user who updated this row
     */
    public final TableField<SecuritySslChallengeRecord, ULong> UPDATED_BY = createField(DSL.name("UPDATED_BY"), SQLDataType.BIGINTUNSIGNED, this, "ID of the user who updated this row");

    /**
     * The column <code>security.security_ssl_challenge.UPDATED_AT</code>. Time
     * when this row is updated
     */
    public final TableField<SecuritySslChallengeRecord, LocalDateTime> UPDATED_AT = createField(DSL.name("UPDATED_AT"), SQLDataType.LOCALDATETIME(0).nullable(false).defaultValue(DSL.field(DSL.raw("CURRENT_TIMESTAMP"), SQLDataType.LOCALDATETIME)), this, "Time when this row is updated");

    private SecuritySslChallenge(Name alias, Table<SecuritySslChallengeRecord> aliased) {
        this(alias, aliased, (Field<?>[]) null, null);
    }

    private SecuritySslChallenge(Name alias, Table<SecuritySslChallengeRecord> aliased, Field<?>[] parameters, Condition where) {
        super(alias, null, aliased, parameters, DSL.comment(""), TableOptions.table(), where);
    }

    /**
     * Create an aliased <code>security.security_ssl_challenge</code> table
     * reference
     */
    public SecuritySslChallenge(String alias) {
        this(DSL.name(alias), SECURITY_SSL_CHALLENGE);
    }

    /**
     * Create an aliased <code>security.security_ssl_challenge</code> table
     * reference
     */
    public SecuritySslChallenge(Name alias) {
        this(alias, SECURITY_SSL_CHALLENGE);
    }

    /**
     * Create a <code>security.security_ssl_challenge</code> table reference
     */
    public SecuritySslChallenge() {
        this(DSL.name("security_ssl_challenge"), null);
    }

    public <O extends Record> SecuritySslChallenge(Table<O> path, ForeignKey<O, SecuritySslChallengeRecord> childPath, InverseForeignKey<O, SecuritySslChallengeRecord> parentPath) {
        super(path, childPath, parentPath, SECURITY_SSL_CHALLENGE);
    }

    /**
     * A subtype implementing {@link Path} for simplified path-based joins.
     */
    public static class SecuritySslChallengePath extends SecuritySslChallenge implements Path<SecuritySslChallengeRecord> {

        private static final long serialVersionUID = 1L;
        public <O extends Record> SecuritySslChallengePath(Table<O> path, ForeignKey<O, SecuritySslChallengeRecord> childPath, InverseForeignKey<O, SecuritySslChallengeRecord> parentPath) {
            super(path, childPath, parentPath);
        }
        private SecuritySslChallengePath(Name alias, Table<SecuritySslChallengeRecord> aliased) {
            super(alias, aliased);
        }

        @Override
        public SecuritySslChallengePath as(String alias) {
            return new SecuritySslChallengePath(DSL.name(alias), this);
        }

        @Override
        public SecuritySslChallengePath as(Name alias) {
            return new SecuritySslChallengePath(alias, this);
        }

        @Override
        public SecuritySslChallengePath as(Table<?> alias) {
            return new SecuritySslChallengePath(alias.getQualifiedName(), this);
        }
    }

    @Override
    public Schema getSchema() {
        return aliased() ? null : Security.SECURITY;
    }

    @Override
    public Identity<SecuritySslChallengeRecord, ULong> getIdentity() {
        return (Identity<SecuritySslChallengeRecord, ULong>) super.getIdentity();
    }

    @Override
    public UniqueKey<SecuritySslChallengeRecord> getPrimaryKey() {
        return Keys.KEY_SECURITY_SSL_CHALLENGE_PRIMARY;
    }

    @Override
    public List<ForeignKey<SecuritySslChallengeRecord, ?>> getReferences() {
        return Arrays.asList(Keys.FK1_SSL_CHLNG_REQ_ID);
    }

    private transient SecuritySslRequestPath _securitySslRequest;

    /**
     * Get the implicit join path to the
     * <code>security.security_ssl_request</code> table.
     */
    public SecuritySslRequestPath securitySslRequest() {
        if (_securitySslRequest == null)
            _securitySslRequest = new SecuritySslRequestPath(this, Keys.FK1_SSL_CHLNG_REQ_ID, null);

        return _securitySslRequest;
    }

    @Override
    public SecuritySslChallenge as(String alias) {
        return new SecuritySslChallenge(DSL.name(alias), this);
    }

    @Override
    public SecuritySslChallenge as(Name alias) {
        return new SecuritySslChallenge(alias, this);
    }

    @Override
    public SecuritySslChallenge as(Table<?> alias) {
        return new SecuritySslChallenge(alias.getQualifiedName(), this);
    }

    /**
     * Rename this table
     */
    @Override
    public SecuritySslChallenge rename(String name) {
        return new SecuritySslChallenge(DSL.name(name), null);
    }

    /**
     * Rename this table
     */
    @Override
    public SecuritySslChallenge rename(Name name) {
        return new SecuritySslChallenge(name, null);
    }

    /**
     * Rename this table
     */
    @Override
    public SecuritySslChallenge rename(Table<?> name) {
        return new SecuritySslChallenge(name.getQualifiedName(), null);
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public SecuritySslChallenge where(Condition condition) {
        return new SecuritySslChallenge(getQualifiedName(), aliased() ? this : null, null, condition);
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public SecuritySslChallenge where(Collection<? extends Condition> conditions) {
        return where(DSL.and(conditions));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public SecuritySslChallenge where(Condition... conditions) {
        return where(DSL.and(conditions));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public SecuritySslChallenge where(Field<Boolean> condition) {
        return where(DSL.condition(condition));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    @PlainSQL
    public SecuritySslChallenge where(SQL condition) {
        return where(DSL.condition(condition));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    @PlainSQL
    public SecuritySslChallenge where(@Stringly.SQL String condition) {
        return where(DSL.condition(condition));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    @PlainSQL
    public SecuritySslChallenge where(@Stringly.SQL String condition, Object... binds) {
        return where(DSL.condition(condition, binds));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    @PlainSQL
    public SecuritySslChallenge where(@Stringly.SQL String condition, QueryPart... parts) {
        return where(DSL.condition(condition, parts));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public SecuritySslChallenge whereExists(Select<?> select) {
        return where(DSL.exists(select));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public SecuritySslChallenge whereNotExists(Select<?> select) {
        return where(DSL.notExists(select));
    }
}
