/*
 * This file is generated by jOOQ.
 */
package com.fincity.security.jooq.tables.records;


import com.fincity.security.jooq.tables.SecurityAppRegIntegrationTokens;

import java.time.LocalDateTime;

import org.jooq.Field;
import org.jooq.JSON;
import org.jooq.Record1;
import org.jooq.Record14;
import org.jooq.Row14;
import org.jooq.impl.UpdatableRecordImpl;
import org.jooq.types.ULong;


/**
 * This class is generated by jOOQ.
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes" })
public class SecurityAppRegIntegrationTokensRecord extends UpdatableRecordImpl<SecurityAppRegIntegrationTokensRecord> implements Record14<ULong, ULong, String, String, String, String, LocalDateTime, JSON, String, JSON, LocalDateTime, ULong, ULong, LocalDateTime> {

    private static final long serialVersionUID = 1L;

    /**
     * Setter for <code>security.security_app_reg_integration_tokens.ID</code>.
     * Primary key
     */
    public SecurityAppRegIntegrationTokensRecord setId(ULong value) {
        set(0, value);
        return this;
    }

    /**
     * Getter for <code>security.security_app_reg_integration_tokens.ID</code>.
     * Primary key
     */
    public ULong getId() {
        return (ULong) get(0);
    }

    /**
     * Setter for
     * <code>security.security_app_reg_integration_tokens.INTEGRATION_ID</code>.
     * Integration ID
     */
    public SecurityAppRegIntegrationTokensRecord setIntegrationId(ULong value) {
        set(1, value);
        return this;
    }

    /**
     * Getter for
     * <code>security.security_app_reg_integration_tokens.INTEGRATION_ID</code>.
     * Integration ID
     */
    public ULong getIntegrationId() {
        return (ULong) get(1);
    }

    /**
     * Setter for
     * <code>security.security_app_reg_integration_tokens.AUTH_CODE</code>. User
     * Consent Auth Code
     */
    public SecurityAppRegIntegrationTokensRecord setAuthCode(String value) {
        set(2, value);
        return this;
    }

    /**
     * Getter for
     * <code>security.security_app_reg_integration_tokens.AUTH_CODE</code>. User
     * Consent Auth Code
     */
    public String getAuthCode() {
        return (String) get(2);
    }

    /**
     * Setter for
     * <code>security.security_app_reg_integration_tokens.STATE</code>. Session
     * id for login
     */
    public SecurityAppRegIntegrationTokensRecord setState(String value) {
        set(3, value);
        return this;
    }

    /**
     * Getter for
     * <code>security.security_app_reg_integration_tokens.STATE</code>. Session
     * id for login
     */
    public String getState() {
        return (String) get(3);
    }

    /**
     * Setter for
     * <code>security.security_app_reg_integration_tokens.TOKEN</code>. Token
     */
    public SecurityAppRegIntegrationTokensRecord setToken(String value) {
        set(4, value);
        return this;
    }

    /**
     * Getter for
     * <code>security.security_app_reg_integration_tokens.TOKEN</code>. Token
     */
    public String getToken() {
        return (String) get(4);
    }

    /**
     * Setter for
     * <code>security.security_app_reg_integration_tokens.REFRESH_TOKEN</code>.
     * Refresh Token
     */
    public SecurityAppRegIntegrationTokensRecord setRefreshToken(String value) {
        set(5, value);
        return this;
    }

    /**
     * Getter for
     * <code>security.security_app_reg_integration_tokens.REFRESH_TOKEN</code>.
     * Refresh Token
     */
    public String getRefreshToken() {
        return (String) get(5);
    }

    /**
     * Setter for
     * <code>security.security_app_reg_integration_tokens.EXPIRES_AT</code>.
     * Token expiration time
     */
    public SecurityAppRegIntegrationTokensRecord setExpiresAt(LocalDateTime value) {
        set(6, value);
        return this;
    }

    /**
     * Getter for
     * <code>security.security_app_reg_integration_tokens.EXPIRES_AT</code>.
     * Token expiration time
     */
    public LocalDateTime getExpiresAt() {
        return (LocalDateTime) get(6);
    }

    /**
     * Setter for
     * <code>security.security_app_reg_integration_tokens.TOKEN_METADATA</code>.
     * Token metadata
     */
    public SecurityAppRegIntegrationTokensRecord setTokenMetadata(JSON value) {
        set(7, value);
        return this;
    }

    /**
     * Getter for
     * <code>security.security_app_reg_integration_tokens.TOKEN_METADATA</code>.
     * Token metadata
     */
    public JSON getTokenMetadata() {
        return (JSON) get(7);
    }

    /**
     * Setter for
     * <code>security.security_app_reg_integration_tokens.USERNAME</code>.
     * Username
     */
    public SecurityAppRegIntegrationTokensRecord setUsername(String value) {
        set(8, value);
        return this;
    }

    /**
     * Getter for
     * <code>security.security_app_reg_integration_tokens.USERNAME</code>.
     * Username
     */
    public String getUsername() {
        return (String) get(8);
    }

    /**
     * Setter for
     * <code>security.security_app_reg_integration_tokens.USER_METADATA</code>.
     * User metadata
     */
    public SecurityAppRegIntegrationTokensRecord setUserMetadata(JSON value) {
        set(9, value);
        return this;
    }

    /**
     * Getter for
     * <code>security.security_app_reg_integration_tokens.USER_METADATA</code>.
     * User metadata
     */
    public JSON getUserMetadata() {
        return (JSON) get(9);
    }

    /**
     * Setter for
     * <code>security.security_app_reg_integration_tokens.CREATED_AT</code>.
     * Time when this row is created
     */
    public SecurityAppRegIntegrationTokensRecord setCreatedAt(LocalDateTime value) {
        set(10, value);
        return this;
    }

    /**
     * Getter for
     * <code>security.security_app_reg_integration_tokens.CREATED_AT</code>.
     * Time when this row is created
     */
    public LocalDateTime getCreatedAt() {
        return (LocalDateTime) get(10);
    }

    /**
     * Setter for
     * <code>security.security_app_reg_integration_tokens.CREATED_BY</code>. ID
     * of the user who created this row
     */
    public SecurityAppRegIntegrationTokensRecord setCreatedBy(ULong value) {
        set(11, value);
        return this;
    }

    /**
     * Getter for
     * <code>security.security_app_reg_integration_tokens.CREATED_BY</code>. ID
     * of the user who created this row
     */
    public ULong getCreatedBy() {
        return (ULong) get(11);
    }

    /**
     * Setter for
     * <code>security.security_app_reg_integration_tokens.UPDATED_BY</code>. ID
     * of the user who updated this row
     */
    public SecurityAppRegIntegrationTokensRecord setUpdatedBy(ULong value) {
        set(12, value);
        return this;
    }

    /**
     * Getter for
     * <code>security.security_app_reg_integration_tokens.UPDATED_BY</code>. ID
     * of the user who updated this row
     */
    public ULong getUpdatedBy() {
        return (ULong) get(12);
    }

    /**
     * Setter for
     * <code>security.security_app_reg_integration_tokens.UPDATED_AT</code>.
     * Time when this row is updated
     */
    public SecurityAppRegIntegrationTokensRecord setUpdatedAt(LocalDateTime value) {
        set(13, value);
        return this;
    }

    /**
     * Getter for
     * <code>security.security_app_reg_integration_tokens.UPDATED_AT</code>.
     * Time when this row is updated
     */
    public LocalDateTime getUpdatedAt() {
        return (LocalDateTime) get(13);
    }

    // -------------------------------------------------------------------------
    // Primary key information
    // -------------------------------------------------------------------------

    @Override
    public Record1<ULong> key() {
        return (Record1) super.key();
    }

    // -------------------------------------------------------------------------
    // Record14 type implementation
    // -------------------------------------------------------------------------

    @Override
    public Row14<ULong, ULong, String, String, String, String, LocalDateTime, JSON, String, JSON, LocalDateTime, ULong, ULong, LocalDateTime> fieldsRow() {
        return (Row14) super.fieldsRow();
    }

    @Override
    public Row14<ULong, ULong, String, String, String, String, LocalDateTime, JSON, String, JSON, LocalDateTime, ULong, ULong, LocalDateTime> valuesRow() {
        return (Row14) super.valuesRow();
    }

    @Override
    public Field<ULong> field1() {
        return SecurityAppRegIntegrationTokens.SECURITY_APP_REG_INTEGRATION_TOKENS.ID;
    }

    @Override
    public Field<ULong> field2() {
        return SecurityAppRegIntegrationTokens.SECURITY_APP_REG_INTEGRATION_TOKENS.INTEGRATION_ID;
    }

    @Override
    public Field<String> field3() {
        return SecurityAppRegIntegrationTokens.SECURITY_APP_REG_INTEGRATION_TOKENS.AUTH_CODE;
    }

    @Override
    public Field<String> field4() {
        return SecurityAppRegIntegrationTokens.SECURITY_APP_REG_INTEGRATION_TOKENS.STATE;
    }

    @Override
    public Field<String> field5() {
        return SecurityAppRegIntegrationTokens.SECURITY_APP_REG_INTEGRATION_TOKENS.TOKEN;
    }

    @Override
    public Field<String> field6() {
        return SecurityAppRegIntegrationTokens.SECURITY_APP_REG_INTEGRATION_TOKENS.REFRESH_TOKEN;
    }

    @Override
    public Field<LocalDateTime> field7() {
        return SecurityAppRegIntegrationTokens.SECURITY_APP_REG_INTEGRATION_TOKENS.EXPIRES_AT;
    }

    @Override
    public Field<JSON> field8() {
        return SecurityAppRegIntegrationTokens.SECURITY_APP_REG_INTEGRATION_TOKENS.TOKEN_METADATA;
    }

    @Override
    public Field<String> field9() {
        return SecurityAppRegIntegrationTokens.SECURITY_APP_REG_INTEGRATION_TOKENS.USERNAME;
    }

    @Override
    public Field<JSON> field10() {
        return SecurityAppRegIntegrationTokens.SECURITY_APP_REG_INTEGRATION_TOKENS.USER_METADATA;
    }

    @Override
    public Field<LocalDateTime> field11() {
        return SecurityAppRegIntegrationTokens.SECURITY_APP_REG_INTEGRATION_TOKENS.CREATED_AT;
    }

    @Override
    public Field<ULong> field12() {
        return SecurityAppRegIntegrationTokens.SECURITY_APP_REG_INTEGRATION_TOKENS.CREATED_BY;
    }

    @Override
    public Field<ULong> field13() {
        return SecurityAppRegIntegrationTokens.SECURITY_APP_REG_INTEGRATION_TOKENS.UPDATED_BY;
    }

    @Override
    public Field<LocalDateTime> field14() {
        return SecurityAppRegIntegrationTokens.SECURITY_APP_REG_INTEGRATION_TOKENS.UPDATED_AT;
    }

    @Override
    public ULong component1() {
        return getId();
    }

    @Override
    public ULong component2() {
        return getIntegrationId();
    }

    @Override
    public String component3() {
        return getAuthCode();
    }

    @Override
    public String component4() {
        return getState();
    }

    @Override
    public String component5() {
        return getToken();
    }

    @Override
    public String component6() {
        return getRefreshToken();
    }

    @Override
    public LocalDateTime component7() {
        return getExpiresAt();
    }

    @Override
    public JSON component8() {
        return getTokenMetadata();
    }

    @Override
    public String component9() {
        return getUsername();
    }

    @Override
    public JSON component10() {
        return getUserMetadata();
    }

    @Override
    public LocalDateTime component11() {
        return getCreatedAt();
    }

    @Override
    public ULong component12() {
        return getCreatedBy();
    }

    @Override
    public ULong component13() {
        return getUpdatedBy();
    }

    @Override
    public LocalDateTime component14() {
        return getUpdatedAt();
    }

    @Override
    public ULong value1() {
        return getId();
    }

    @Override
    public ULong value2() {
        return getIntegrationId();
    }

    @Override
    public String value3() {
        return getAuthCode();
    }

    @Override
    public String value4() {
        return getState();
    }

    @Override
    public String value5() {
        return getToken();
    }

    @Override
    public String value6() {
        return getRefreshToken();
    }

    @Override
    public LocalDateTime value7() {
        return getExpiresAt();
    }

    @Override
    public JSON value8() {
        return getTokenMetadata();
    }

    @Override
    public String value9() {
        return getUsername();
    }

    @Override
    public JSON value10() {
        return getUserMetadata();
    }

    @Override
    public LocalDateTime value11() {
        return getCreatedAt();
    }

    @Override
    public ULong value12() {
        return getCreatedBy();
    }

    @Override
    public ULong value13() {
        return getUpdatedBy();
    }

    @Override
    public LocalDateTime value14() {
        return getUpdatedAt();
    }

    @Override
    public SecurityAppRegIntegrationTokensRecord value1(ULong value) {
        setId(value);
        return this;
    }

    @Override
    public SecurityAppRegIntegrationTokensRecord value2(ULong value) {
        setIntegrationId(value);
        return this;
    }

    @Override
    public SecurityAppRegIntegrationTokensRecord value3(String value) {
        setAuthCode(value);
        return this;
    }

    @Override
    public SecurityAppRegIntegrationTokensRecord value4(String value) {
        setState(value);
        return this;
    }

    @Override
    public SecurityAppRegIntegrationTokensRecord value5(String value) {
        setToken(value);
        return this;
    }

    @Override
    public SecurityAppRegIntegrationTokensRecord value6(String value) {
        setRefreshToken(value);
        return this;
    }

    @Override
    public SecurityAppRegIntegrationTokensRecord value7(LocalDateTime value) {
        setExpiresAt(value);
        return this;
    }

    @Override
    public SecurityAppRegIntegrationTokensRecord value8(JSON value) {
        setTokenMetadata(value);
        return this;
    }

    @Override
    public SecurityAppRegIntegrationTokensRecord value9(String value) {
        setUsername(value);
        return this;
    }

    @Override
    public SecurityAppRegIntegrationTokensRecord value10(JSON value) {
        setUserMetadata(value);
        return this;
    }

    @Override
    public SecurityAppRegIntegrationTokensRecord value11(LocalDateTime value) {
        setCreatedAt(value);
        return this;
    }

    @Override
    public SecurityAppRegIntegrationTokensRecord value12(ULong value) {
        setCreatedBy(value);
        return this;
    }

    @Override
    public SecurityAppRegIntegrationTokensRecord value13(ULong value) {
        setUpdatedBy(value);
        return this;
    }

    @Override
    public SecurityAppRegIntegrationTokensRecord value14(LocalDateTime value) {
        setUpdatedAt(value);
        return this;
    }

    @Override
    public SecurityAppRegIntegrationTokensRecord values(ULong value1, ULong value2, String value3, String value4, String value5, String value6, LocalDateTime value7, JSON value8, String value9, JSON value10, LocalDateTime value11, ULong value12, ULong value13, LocalDateTime value14) {
        value1(value1);
        value2(value2);
        value3(value3);
        value4(value4);
        value5(value5);
        value6(value6);
        value7(value7);
        value8(value8);
        value9(value9);
        value10(value10);
        value11(value11);
        value12(value12);
        value13(value13);
        value14(value14);
        return this;
    }

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /**
     * Create a detached SecurityAppRegIntegrationTokensRecord
     */
    public SecurityAppRegIntegrationTokensRecord() {
        super(SecurityAppRegIntegrationTokens.SECURITY_APP_REG_INTEGRATION_TOKENS);
    }

    /**
     * Create a detached, initialised SecurityAppRegIntegrationTokensRecord
     */
    public SecurityAppRegIntegrationTokensRecord(ULong id, ULong integrationId, String authCode, String state, String token, String refreshToken, LocalDateTime expiresAt, JSON tokenMetadata, String username, JSON userMetadata, LocalDateTime createdAt, ULong createdBy, ULong updatedBy, LocalDateTime updatedAt) {
        super(SecurityAppRegIntegrationTokens.SECURITY_APP_REG_INTEGRATION_TOKENS);

        setId(id);
        setIntegrationId(integrationId);
        setAuthCode(authCode);
        setState(state);
        setToken(token);
        setRefreshToken(refreshToken);
        setExpiresAt(expiresAt);
        setTokenMetadata(tokenMetadata);
        setUsername(username);
        setUserMetadata(userMetadata);
        setCreatedAt(createdAt);
        setCreatedBy(createdBy);
        setUpdatedBy(updatedBy);
        setUpdatedAt(updatedAt);
        resetChangedOnNotNull();
    }
}
