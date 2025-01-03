/*
 * This file is generated by jOOQ.
 */
package com.fincity.security.jooq.tables;


import com.fincity.security.jooq.Keys;
import com.fincity.security.jooq.Security;
import com.fincity.security.jooq.tables.SecurityClientUrl.SecurityClientUrlPath;
import com.fincity.security.jooq.tables.SecuritySslChallenge.SecuritySslChallengePath;
import com.fincity.security.jooq.tables.records.SecuritySslRequestRecord;

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
public class SecuritySslRequest extends TableImpl<SecuritySslRequestRecord> {

    private static final long serialVersionUID = 1L;

    /**
     * The reference instance of <code>security.security_ssl_request</code>
     */
    public static final SecuritySslRequest SECURITY_SSL_REQUEST = new SecuritySslRequest();

    /**
     * The class holding records for this type
     */
    @Override
    public Class<SecuritySslRequestRecord> getRecordType() {
        return SecuritySslRequestRecord.class;
    }

    /**
     * The column <code>security.security_ssl_request.ID</code>. Primary key
     */
    public final TableField<SecuritySslRequestRecord, ULong> ID = createField(DSL.name("ID"), SQLDataType.BIGINTUNSIGNED.nullable(false).identity(true), this, "Primary key");

    /**
     * The column <code>security.security_ssl_request.URL_ID</code>. URL ID for
     * which this SSL certificate belongs to
     */
    public final TableField<SecuritySslRequestRecord, ULong> URL_ID = createField(DSL.name("URL_ID"), SQLDataType.BIGINTUNSIGNED.nullable(false), this, "URL ID for which this SSL certificate belongs to");

    /**
     * The column <code>security.security_ssl_request.DOMAINS</code>. Domains
     * for which this SSL certificate is valid
     */
    public final TableField<SecuritySslRequestRecord, String> DOMAINS = createField(DSL.name("DOMAINS"), SQLDataType.VARCHAR(1024).nullable(false), this, "Domains for which this SSL certificate is valid");

    /**
     * The column <code>security.security_ssl_request.ORGANIZATION</code>.
     * Organization for which this SSL certificate is valid
     */
    public final TableField<SecuritySslRequestRecord, String> ORGANIZATION = createField(DSL.name("ORGANIZATION"), SQLDataType.VARCHAR(1024).nullable(false), this, "Organization for which this SSL certificate is valid");

    /**
     * The column <code>security.security_ssl_request.CRT_KEY</code>. SSL
     * certificate key
     */
    public final TableField<SecuritySslRequestRecord, String> CRT_KEY = createField(DSL.name("CRT_KEY"), SQLDataType.CLOB.nullable(false), this, "SSL certificate key");

    /**
     * The column <code>security.security_ssl_request.CSR</code>. SSL
     * certificate signing request
     */
    public final TableField<SecuritySslRequestRecord, String> CSR = createField(DSL.name("CSR"), SQLDataType.CLOB.nullable(false), this, "SSL certificate signing request");

    /**
     * The column <code>security.security_ssl_request.VALIDITY</code>. Validity
     * of the SSL certificate in months
     */
    public final TableField<SecuritySslRequestRecord, UInteger> VALIDITY = createField(DSL.name("VALIDITY"), SQLDataType.INTEGERUNSIGNED.nullable(false), this, "Validity of the SSL certificate in months");

    /**
     * The column <code>security.security_ssl_request.FAILED_REASON</code>.
     * Reason for challenge failure
     */
    public final TableField<SecuritySslRequestRecord, String> FAILED_REASON = createField(DSL.name("FAILED_REASON"), SQLDataType.CLOB, this, "Reason for challenge failure");

    /**
     * The column <code>security.security_ssl_request.UPDATED_BY</code>. ID of
     * the user who updated this row
     */
    public final TableField<SecuritySslRequestRecord, ULong> UPDATED_BY = createField(DSL.name("UPDATED_BY"), SQLDataType.BIGINTUNSIGNED, this, "ID of the user who updated this row");

    /**
     * The column <code>security.security_ssl_request.UPDATED_AT</code>. Time
     * when this row is updated
     */
    public final TableField<SecuritySslRequestRecord, LocalDateTime> UPDATED_AT = createField(DSL.name("UPDATED_AT"), SQLDataType.LOCALDATETIME(0).nullable(false).defaultValue(DSL.field(DSL.raw("CURRENT_TIMESTAMP"), SQLDataType.LOCALDATETIME)), this, "Time when this row is updated");

    private SecuritySslRequest(Name alias, Table<SecuritySslRequestRecord> aliased) {
        this(alias, aliased, (Field<?>[]) null, null);
    }

    private SecuritySslRequest(Name alias, Table<SecuritySslRequestRecord> aliased, Field<?>[] parameters, Condition where) {
        super(alias, null, aliased, parameters, DSL.comment(""), TableOptions.table(), where);
    }

    /**
     * Create an aliased <code>security.security_ssl_request</code> table
     * reference
     */
    public SecuritySslRequest(String alias) {
        this(DSL.name(alias), SECURITY_SSL_REQUEST);
    }

    /**
     * Create an aliased <code>security.security_ssl_request</code> table
     * reference
     */
    public SecuritySslRequest(Name alias) {
        this(alias, SECURITY_SSL_REQUEST);
    }

    /**
     * Create a <code>security.security_ssl_request</code> table reference
     */
    public SecuritySslRequest() {
        this(DSL.name("security_ssl_request"), null);
    }

    public <O extends Record> SecuritySslRequest(Table<O> path, ForeignKey<O, SecuritySslRequestRecord> childPath, InverseForeignKey<O, SecuritySslRequestRecord> parentPath) {
        super(path, childPath, parentPath, SECURITY_SSL_REQUEST);
    }

    /**
     * A subtype implementing {@link Path} for simplified path-based joins.
     */
    public static class SecuritySslRequestPath extends SecuritySslRequest implements Path<SecuritySslRequestRecord> {

        private static final long serialVersionUID = 1L;
        public <O extends Record> SecuritySslRequestPath(Table<O> path, ForeignKey<O, SecuritySslRequestRecord> childPath, InverseForeignKey<O, SecuritySslRequestRecord> parentPath) {
            super(path, childPath, parentPath);
        }
        private SecuritySslRequestPath(Name alias, Table<SecuritySslRequestRecord> aliased) {
            super(alias, aliased);
        }

        @Override
        public SecuritySslRequestPath as(String alias) {
            return new SecuritySslRequestPath(DSL.name(alias), this);
        }

        @Override
        public SecuritySslRequestPath as(Name alias) {
            return new SecuritySslRequestPath(alias, this);
        }

        @Override
        public SecuritySslRequestPath as(Table<?> alias) {
            return new SecuritySslRequestPath(alias.getQualifiedName(), this);
        }
    }

    @Override
    public Schema getSchema() {
        return aliased() ? null : Security.SECURITY;
    }

    @Override
    public Identity<SecuritySslRequestRecord, ULong> getIdentity() {
        return (Identity<SecuritySslRequestRecord, ULong>) super.getIdentity();
    }

    @Override
    public UniqueKey<SecuritySslRequestRecord> getPrimaryKey() {
        return Keys.KEY_SECURITY_SSL_REQUEST_PRIMARY;
    }

    @Override
    public List<UniqueKey<SecuritySslRequestRecord>> getUniqueKeys() {
        return Arrays.asList(Keys.KEY_SECURITY_SSL_REQUEST_URL_ID);
    }

    @Override
    public List<ForeignKey<SecuritySslRequestRecord, ?>> getReferences() {
        return Arrays.asList(Keys.FK1_SSL_REQ_CLNT_URL_ID);
    }

    private transient SecurityClientUrlPath _securityClientUrl;

    /**
     * Get the implicit join path to the
     * <code>security.security_client_url</code> table.
     */
    public SecurityClientUrlPath securityClientUrl() {
        if (_securityClientUrl == null)
            _securityClientUrl = new SecurityClientUrlPath(this, Keys.FK1_SSL_REQ_CLNT_URL_ID, null);

        return _securityClientUrl;
    }

    private transient SecuritySslChallengePath _securitySslChallenge;

    /**
     * Get the implicit to-many join path to the
     * <code>security.security_ssl_challenge</code> table
     */
    public SecuritySslChallengePath securitySslChallenge() {
        if (_securitySslChallenge == null)
            _securitySslChallenge = new SecuritySslChallengePath(this, null, Keys.FK1_SSL_CHLNG_REQ_ID.getInverseKey());

        return _securitySslChallenge;
    }

    @Override
    public SecuritySslRequest as(String alias) {
        return new SecuritySslRequest(DSL.name(alias), this);
    }

    @Override
    public SecuritySslRequest as(Name alias) {
        return new SecuritySslRequest(alias, this);
    }

    @Override
    public SecuritySslRequest as(Table<?> alias) {
        return new SecuritySslRequest(alias.getQualifiedName(), this);
    }

    /**
     * Rename this table
     */
    @Override
    public SecuritySslRequest rename(String name) {
        return new SecuritySslRequest(DSL.name(name), null);
    }

    /**
     * Rename this table
     */
    @Override
    public SecuritySslRequest rename(Name name) {
        return new SecuritySslRequest(name, null);
    }

    /**
     * Rename this table
     */
    @Override
    public SecuritySslRequest rename(Table<?> name) {
        return new SecuritySslRequest(name.getQualifiedName(), null);
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public SecuritySslRequest where(Condition condition) {
        return new SecuritySslRequest(getQualifiedName(), aliased() ? this : null, null, condition);
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public SecuritySslRequest where(Collection<? extends Condition> conditions) {
        return where(DSL.and(conditions));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public SecuritySslRequest where(Condition... conditions) {
        return where(DSL.and(conditions));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public SecuritySslRequest where(Field<Boolean> condition) {
        return where(DSL.condition(condition));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    @PlainSQL
    public SecuritySslRequest where(SQL condition) {
        return where(DSL.condition(condition));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    @PlainSQL
    public SecuritySslRequest where(@Stringly.SQL String condition) {
        return where(DSL.condition(condition));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    @PlainSQL
    public SecuritySslRequest where(@Stringly.SQL String condition, Object... binds) {
        return where(DSL.condition(condition, binds));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    @PlainSQL
    public SecuritySslRequest where(@Stringly.SQL String condition, QueryPart... parts) {
        return where(DSL.condition(condition, parts));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public SecuritySslRequest whereExists(Select<?> select) {
        return where(DSL.exists(select));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public SecuritySslRequest whereNotExists(Select<?> select) {
        return where(DSL.notExists(select));
    }
}
