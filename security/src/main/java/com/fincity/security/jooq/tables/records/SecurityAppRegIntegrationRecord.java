/*
 * This file is generated by jOOQ.
 */
package com.fincity.security.jooq.tables.records;


import com.fincity.security.jooq.tables.SecurityAppRegIntegration;

import java.time.LocalDateTime;

import org.jooq.Field;
import org.jooq.Record1;
import org.jooq.Record6;
import org.jooq.Row6;
import org.jooq.impl.UpdatableRecordImpl;
import org.jooq.types.ULong;


/**
 * This class is generated by jOOQ.
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes" })
public class SecurityAppRegIntegrationRecord extends UpdatableRecordImpl<SecurityAppRegIntegrationRecord> implements Record6<ULong, ULong, ULong, ULong, ULong, LocalDateTime> {

    private static final long serialVersionUID = 1L;

    /**
     * Setter for <code>security.security_app_reg_integration.ID</code>. Primary
     * key
     */
    public SecurityAppRegIntegrationRecord setId(ULong value) {
        set(0, value);
        return this;
    }

    /**
     * Getter for <code>security.security_app_reg_integration.ID</code>. Primary
     * key
     */
    public ULong getId() {
        return (ULong) get(0);
    }

    /**
     * Setter for <code>security.security_app_reg_integration.APP_ID</code>. App
     * ID
     */
    public SecurityAppRegIntegrationRecord setAppId(ULong value) {
        set(1, value);
        return this;
    }

    /**
     * Getter for <code>security.security_app_reg_integration.APP_ID</code>. App
     * ID
     */
    public ULong getAppId() {
        return (ULong) get(1);
    }

    /**
     * Setter for <code>security.security_app_reg_integration.CLIENT_ID</code>.
     * Client ID
     */
    public SecurityAppRegIntegrationRecord setClientId(ULong value) {
        set(2, value);
        return this;
    }

    /**
     * Getter for <code>security.security_app_reg_integration.CLIENT_ID</code>.
     * Client ID
     */
    public ULong getClientId() {
        return (ULong) get(2);
    }

    /**
     * Setter for
     * <code>security.security_app_reg_integration.INTEGRATION_ID</code>.
     * Integration ID
     */
    public SecurityAppRegIntegrationRecord setIntegrationId(ULong value) {
        set(3, value);
        return this;
    }

    /**
     * Getter for
     * <code>security.security_app_reg_integration.INTEGRATION_ID</code>.
     * Integration ID
     */
    public ULong getIntegrationId() {
        return (ULong) get(3);
    }

    /**
     * Setter for <code>security.security_app_reg_integration.CREATED_BY</code>.
     * ID of the user who created this row
     */
    public SecurityAppRegIntegrationRecord setCreatedBy(ULong value) {
        set(4, value);
        return this;
    }

    /**
     * Getter for <code>security.security_app_reg_integration.CREATED_BY</code>.
     * ID of the user who created this row
     */
    public ULong getCreatedBy() {
        return (ULong) get(4);
    }

    /**
     * Setter for <code>security.security_app_reg_integration.CREATED_AT</code>.
     * Time when this row is created
     */
    public SecurityAppRegIntegrationRecord setCreatedAt(LocalDateTime value) {
        set(5, value);
        return this;
    }

    /**
     * Getter for <code>security.security_app_reg_integration.CREATED_AT</code>.
     * Time when this row is created
     */
    public LocalDateTime getCreatedAt() {
        return (LocalDateTime) get(5);
    }

    // -------------------------------------------------------------------------
    // Primary key information
    // -------------------------------------------------------------------------

    @Override
    public Record1<ULong> key() {
        return (Record1) super.key();
    }

    // -------------------------------------------------------------------------
    // Record6 type implementation
    // -------------------------------------------------------------------------

    @Override
    public Row6<ULong, ULong, ULong, ULong, ULong, LocalDateTime> fieldsRow() {
        return (Row6) super.fieldsRow();
    }

    @Override
    public Row6<ULong, ULong, ULong, ULong, ULong, LocalDateTime> valuesRow() {
        return (Row6) super.valuesRow();
    }

    @Override
    public Field<ULong> field1() {
        return SecurityAppRegIntegration.SECURITY_APP_REG_INTEGRATION.ID;
    }

    @Override
    public Field<ULong> field2() {
        return SecurityAppRegIntegration.SECURITY_APP_REG_INTEGRATION.APP_ID;
    }

    @Override
    public Field<ULong> field3() {
        return SecurityAppRegIntegration.SECURITY_APP_REG_INTEGRATION.CLIENT_ID;
    }

    @Override
    public Field<ULong> field4() {
        return SecurityAppRegIntegration.SECURITY_APP_REG_INTEGRATION.INTEGRATION_ID;
    }

    @Override
    public Field<ULong> field5() {
        return SecurityAppRegIntegration.SECURITY_APP_REG_INTEGRATION.CREATED_BY;
    }

    @Override
    public Field<LocalDateTime> field6() {
        return SecurityAppRegIntegration.SECURITY_APP_REG_INTEGRATION.CREATED_AT;
    }

    @Override
    public ULong component1() {
        return getId();
    }

    @Override
    public ULong component2() {
        return getAppId();
    }

    @Override
    public ULong component3() {
        return getClientId();
    }

    @Override
    public ULong component4() {
        return getIntegrationId();
    }

    @Override
    public ULong component5() {
        return getCreatedBy();
    }

    @Override
    public LocalDateTime component6() {
        return getCreatedAt();
    }

    @Override
    public ULong value1() {
        return getId();
    }

    @Override
    public ULong value2() {
        return getAppId();
    }

    @Override
    public ULong value3() {
        return getClientId();
    }

    @Override
    public ULong value4() {
        return getIntegrationId();
    }

    @Override
    public ULong value5() {
        return getCreatedBy();
    }

    @Override
    public LocalDateTime value6() {
        return getCreatedAt();
    }

    @Override
    public SecurityAppRegIntegrationRecord value1(ULong value) {
        setId(value);
        return this;
    }

    @Override
    public SecurityAppRegIntegrationRecord value2(ULong value) {
        setAppId(value);
        return this;
    }

    @Override
    public SecurityAppRegIntegrationRecord value3(ULong value) {
        setClientId(value);
        return this;
    }

    @Override
    public SecurityAppRegIntegrationRecord value4(ULong value) {
        setIntegrationId(value);
        return this;
    }

    @Override
    public SecurityAppRegIntegrationRecord value5(ULong value) {
        setCreatedBy(value);
        return this;
    }

    @Override
    public SecurityAppRegIntegrationRecord value6(LocalDateTime value) {
        setCreatedAt(value);
        return this;
    }

    @Override
    public SecurityAppRegIntegrationRecord values(ULong value1, ULong value2, ULong value3, ULong value4, ULong value5, LocalDateTime value6) {
        value1(value1);
        value2(value2);
        value3(value3);
        value4(value4);
        value5(value5);
        value6(value6);
        return this;
    }

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /**
     * Create a detached SecurityAppRegIntegrationRecord
     */
    public SecurityAppRegIntegrationRecord() {
        super(SecurityAppRegIntegration.SECURITY_APP_REG_INTEGRATION);
    }

    /**
     * Create a detached, initialised SecurityAppRegIntegrationRecord
     */
    public SecurityAppRegIntegrationRecord(ULong id, ULong appId, ULong clientId, ULong integrationId, ULong createdBy, LocalDateTime createdAt) {
        super(SecurityAppRegIntegration.SECURITY_APP_REG_INTEGRATION);

        setId(id);
        setAppId(appId);
        setClientId(clientId);
        setIntegrationId(integrationId);
        setCreatedBy(createdBy);
        setCreatedAt(createdAt);
        resetChangedOnNotNull();
    }
}