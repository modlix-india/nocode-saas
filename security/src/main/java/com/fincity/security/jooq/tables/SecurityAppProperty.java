/*
 * This file is generated by jOOQ.
 */
package com.fincity.security.jooq.tables;


import com.fincity.security.jooq.Keys;
import com.fincity.security.jooq.Security;
import com.fincity.security.jooq.tables.SecurityApp.SecurityAppPath;
import com.fincity.security.jooq.tables.SecurityClient.SecurityClientPath;
import com.fincity.security.jooq.tables.records.SecurityAppPropertyRecord;

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
public class SecurityAppProperty extends TableImpl<SecurityAppPropertyRecord> {

    private static final long serialVersionUID = 1L;

    /**
     * The reference instance of <code>security.security_app_property</code>
     */
    public static final SecurityAppProperty SECURITY_APP_PROPERTY = new SecurityAppProperty();

    /**
     * The class holding records for this type
     */
    @Override
    public Class<SecurityAppPropertyRecord> getRecordType() {
        return SecurityAppPropertyRecord.class;
    }

    /**
     * The column <code>security.security_app_property.ID</code>. Primary key
     */
    public final TableField<SecurityAppPropertyRecord, ULong> ID = createField(DSL.name("ID"), SQLDataType.BIGINTUNSIGNED.nullable(false).identity(true), this, "Primary key");

    /**
     * The column <code>security.security_app_property.APP_ID</code>. App ID for
     * which this property belongs to
     */
    public final TableField<SecurityAppPropertyRecord, ULong> APP_ID = createField(DSL.name("APP_ID"), SQLDataType.BIGINTUNSIGNED.nullable(false), this, "App ID for which this property belongs to");

    /**
     * The column <code>security.security_app_property.CLIENT_ID</code>. Client
     * ID for which this property belongs to
     */
    public final TableField<SecurityAppPropertyRecord, ULong> CLIENT_ID = createField(DSL.name("CLIENT_ID"), SQLDataType.BIGINTUNSIGNED.nullable(false), this, "Client ID for which this property belongs to");

    /**
     * The column <code>security.security_app_property.NAME</code>. Name of the
     * property
     */
    public final TableField<SecurityAppPropertyRecord, String> NAME = createField(DSL.name("NAME"), SQLDataType.VARCHAR(128).nullable(false), this, "Name of the property");

    /**
     * The column <code>security.security_app_property.VALUE</code>. Value of
     * the property
     */
    public final TableField<SecurityAppPropertyRecord, String> VALUE = createField(DSL.name("VALUE"), SQLDataType.CLOB, this, "Value of the property");

    /**
     * The column <code>security.security_app_property.CREATED_BY</code>. ID of
     * the user who created this row
     */
    public final TableField<SecurityAppPropertyRecord, ULong> CREATED_BY = createField(DSL.name("CREATED_BY"), SQLDataType.BIGINTUNSIGNED, this, "ID of the user who created this row");

    /**
     * The column <code>security.security_app_property.CREATED_AT</code>. Time
     * when this row is created
     */
    public final TableField<SecurityAppPropertyRecord, LocalDateTime> CREATED_AT = createField(DSL.name("CREATED_AT"), SQLDataType.LOCALDATETIME(0).nullable(false).defaultValue(DSL.field(DSL.raw("CURRENT_TIMESTAMP"), SQLDataType.LOCALDATETIME)), this, "Time when this row is created");

    /**
     * The column <code>security.security_app_property.UPDATED_BY</code>. ID of
     * the user who updated this row
     */
    public final TableField<SecurityAppPropertyRecord, ULong> UPDATED_BY = createField(DSL.name("UPDATED_BY"), SQLDataType.BIGINTUNSIGNED, this, "ID of the user who updated this row");

    /**
     * The column <code>security.security_app_property.UPDATED_AT</code>. Time
     * when this row is updated
     */
    public final TableField<SecurityAppPropertyRecord, LocalDateTime> UPDATED_AT = createField(DSL.name("UPDATED_AT"), SQLDataType.LOCALDATETIME(0).nullable(false).defaultValue(DSL.field(DSL.raw("CURRENT_TIMESTAMP"), SQLDataType.LOCALDATETIME)), this, "Time when this row is updated");

    private SecurityAppProperty(Name alias, Table<SecurityAppPropertyRecord> aliased) {
        this(alias, aliased, (Field<?>[]) null, null);
    }

    private SecurityAppProperty(Name alias, Table<SecurityAppPropertyRecord> aliased, Field<?>[] parameters, Condition where) {
        super(alias, null, aliased, parameters, DSL.comment(""), TableOptions.table(), where);
    }

    /**
     * Create an aliased <code>security.security_app_property</code> table
     * reference
     */
    public SecurityAppProperty(String alias) {
        this(DSL.name(alias), SECURITY_APP_PROPERTY);
    }

    /**
     * Create an aliased <code>security.security_app_property</code> table
     * reference
     */
    public SecurityAppProperty(Name alias) {
        this(alias, SECURITY_APP_PROPERTY);
    }

    /**
     * Create a <code>security.security_app_property</code> table reference
     */
    public SecurityAppProperty() {
        this(DSL.name("security_app_property"), null);
    }

    public <O extends Record> SecurityAppProperty(Table<O> path, ForeignKey<O, SecurityAppPropertyRecord> childPath, InverseForeignKey<O, SecurityAppPropertyRecord> parentPath) {
        super(path, childPath, parentPath, SECURITY_APP_PROPERTY);
    }

    /**
     * A subtype implementing {@link Path} for simplified path-based joins.
     */
    public static class SecurityAppPropertyPath extends SecurityAppProperty implements Path<SecurityAppPropertyRecord> {

        private static final long serialVersionUID = 1L;
        public <O extends Record> SecurityAppPropertyPath(Table<O> path, ForeignKey<O, SecurityAppPropertyRecord> childPath, InverseForeignKey<O, SecurityAppPropertyRecord> parentPath) {
            super(path, childPath, parentPath);
        }
        private SecurityAppPropertyPath(Name alias, Table<SecurityAppPropertyRecord> aliased) {
            super(alias, aliased);
        }

        @Override
        public SecurityAppPropertyPath as(String alias) {
            return new SecurityAppPropertyPath(DSL.name(alias), this);
        }

        @Override
        public SecurityAppPropertyPath as(Name alias) {
            return new SecurityAppPropertyPath(alias, this);
        }

        @Override
        public SecurityAppPropertyPath as(Table<?> alias) {
            return new SecurityAppPropertyPath(alias.getQualifiedName(), this);
        }
    }

    @Override
    public Schema getSchema() {
        return aliased() ? null : Security.SECURITY;
    }

    @Override
    public Identity<SecurityAppPropertyRecord, ULong> getIdentity() {
        return (Identity<SecurityAppPropertyRecord, ULong>) super.getIdentity();
    }

    @Override
    public UniqueKey<SecurityAppPropertyRecord> getPrimaryKey() {
        return Keys.KEY_SECURITY_APP_PROPERTY_PRIMARY;
    }

    @Override
    public List<UniqueKey<SecurityAppPropertyRecord>> getUniqueKeys() {
        return Arrays.asList(Keys.KEY_SECURITY_APP_PROPERTY_APP_ID);
    }

    @Override
    public List<ForeignKey<SecurityAppPropertyRecord, ?>> getReferences() {
        return Arrays.asList(Keys.FK1_APP_PROP_APP_ID, Keys.FK2_APP_PROP_CLNT_ID);
    }

    private transient SecurityAppPath _securityApp;

    /**
     * Get the implicit join path to the <code>security.security_app</code>
     * table.
     */
    public SecurityAppPath securityApp() {
        if (_securityApp == null)
            _securityApp = new SecurityAppPath(this, Keys.FK1_APP_PROP_APP_ID, null);

        return _securityApp;
    }

    private transient SecurityClientPath _securityClient;

    /**
     * Get the implicit join path to the <code>security.security_client</code>
     * table.
     */
    public SecurityClientPath securityClient() {
        if (_securityClient == null)
            _securityClient = new SecurityClientPath(this, Keys.FK2_APP_PROP_CLNT_ID, null);

        return _securityClient;
    }

    @Override
    public SecurityAppProperty as(String alias) {
        return new SecurityAppProperty(DSL.name(alias), this);
    }

    @Override
    public SecurityAppProperty as(Name alias) {
        return new SecurityAppProperty(alias, this);
    }

    @Override
    public SecurityAppProperty as(Table<?> alias) {
        return new SecurityAppProperty(alias.getQualifiedName(), this);
    }

    /**
     * Rename this table
     */
    @Override
    public SecurityAppProperty rename(String name) {
        return new SecurityAppProperty(DSL.name(name), null);
    }

    /**
     * Rename this table
     */
    @Override
    public SecurityAppProperty rename(Name name) {
        return new SecurityAppProperty(name, null);
    }

    /**
     * Rename this table
     */
    @Override
    public SecurityAppProperty rename(Table<?> name) {
        return new SecurityAppProperty(name.getQualifiedName(), null);
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public SecurityAppProperty where(Condition condition) {
        return new SecurityAppProperty(getQualifiedName(), aliased() ? this : null, null, condition);
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public SecurityAppProperty where(Collection<? extends Condition> conditions) {
        return where(DSL.and(conditions));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public SecurityAppProperty where(Condition... conditions) {
        return where(DSL.and(conditions));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public SecurityAppProperty where(Field<Boolean> condition) {
        return where(DSL.condition(condition));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    @PlainSQL
    public SecurityAppProperty where(SQL condition) {
        return where(DSL.condition(condition));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    @PlainSQL
    public SecurityAppProperty where(@Stringly.SQL String condition) {
        return where(DSL.condition(condition));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    @PlainSQL
    public SecurityAppProperty where(@Stringly.SQL String condition, Object... binds) {
        return where(DSL.condition(condition, binds));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    @PlainSQL
    public SecurityAppProperty where(@Stringly.SQL String condition, QueryPart... parts) {
        return where(DSL.condition(condition, parts));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public SecurityAppProperty whereExists(Select<?> select) {
        return where(DSL.exists(select));
    }

    /**
     * Create an inline derived table from this table
     */
    @Override
    public SecurityAppProperty whereNotExists(Select<?> select) {
        return where(DSL.notExists(select));
    }
}
