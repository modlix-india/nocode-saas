/*
 * This file is generated by jOOQ.
 */
package com.fincity.saas.commons.core.jooq.tables.records;


import com.fincity.saas.commons.core.jooq.enums.CoreTokensTokenType;
import com.fincity.saas.commons.core.jooq.tables.CoreTokens;

import java.time.LocalDateTime;

import org.jooq.JSON;
import org.jooq.Record1;
import org.jooq.impl.UpdatableRecordImpl;
import org.jooq.types.ULong;


/**
 * This class is generated by jOOQ.
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes", "this-escape" })
public class CoreTokensRecord extends UpdatableRecordImpl<CoreTokensRecord> {

    private static final long serialVersionUID = 1L;

    /**
     * Setter for <code>core.core_tokens.ID</code>. Primary key
     */
    public CoreTokensRecord setId(ULong value) {
        set(0, value);
        return this;
    }

    /**
     * Getter for <code>core.core_tokens.ID</code>. Primary key
     */
    public ULong getId() {
        return (ULong) get(0);
    }

    /**
     * Setter for <code>core.core_tokens.USER_ID</code>. User ID
     */
    public CoreTokensRecord setUserId(ULong value) {
        set(1, value);
        return this;
    }

    /**
     * Getter for <code>core.core_tokens.USER_ID</code>. User ID
     */
    public ULong getUserId() {
        return (ULong) get(1);
    }

    /**
     * Setter for <code>core.core_tokens.CLIENT_CODE</code>. Client Code
     */
    public CoreTokensRecord setClientCode(String value) {
        set(2, value);
        return this;
    }

    /**
     * Getter for <code>core.core_tokens.CLIENT_CODE</code>. Client Code
     */
    public String getClientCode() {
        return (String) get(2);
    }

    /**
     * Setter for <code>core.core_tokens.APP_CODE</code>. App Code
     */
    public CoreTokensRecord setAppCode(String value) {
        set(3, value);
        return this;
    }

    /**
     * Getter for <code>core.core_tokens.APP_CODE</code>. App Code
     */
    public String getAppCode() {
        return (String) get(3);
    }

    /**
     * Setter for <code>core.core_tokens.STATE</code>. State of the token
     */
    public CoreTokensRecord setState(String value) {
        set(4, value);
        return this;
    }

    /**
     * Getter for <code>core.core_tokens.STATE</code>. State of the token
     */
    public String getState() {
        return (String) get(4);
    }

    /**
     * Setter for <code>core.core_tokens.CONNECTION_NAME</code>. Connection for
     * which token is generated
     */
    public CoreTokensRecord setConnectionName(String value) {
        set(5, value);
        return this;
    }

    /**
     * Getter for <code>core.core_tokens.CONNECTION_NAME</code>. Connection for
     * which token is generated
     */
    public String getConnectionName() {
        return (String) get(5);
    }

    /**
     * Setter for <code>core.core_tokens.TOKEN_TYPE</code>. Type of token that
     * is generated
     */
    public CoreTokensRecord setTokenType(CoreTokensTokenType value) {
        set(6, value);
        return this;
    }

    /**
     * Getter for <code>core.core_tokens.TOKEN_TYPE</code>. Type of token that
     * is generated
     */
    public CoreTokensTokenType getTokenType() {
        return (CoreTokensTokenType) get(6);
    }

    /**
     * Setter for <code>core.core_tokens.TOKEN</code>. Generated Token
     */
    public CoreTokensRecord setToken(String value) {
        set(7, value);
        return this;
    }

    /**
     * Getter for <code>core.core_tokens.TOKEN</code>. Generated Token
     */
    public String getToken() {
        return (String) get(7);
    }

    /**
     * Setter for <code>core.core_tokens.TOKEN_METADATA</code>. Metadata of the
     * token
     */
    public CoreTokensRecord setTokenMetadata(JSON value) {
        set(8, value);
        return this;
    }

    /**
     * Getter for <code>core.core_tokens.TOKEN_METADATA</code>. Metadata of the
     * token
     */
    public JSON getTokenMetadata() {
        return (JSON) get(8);
    }

    /**
     * Setter for <code>core.core_tokens.IS_REVOKED</code>. If false, means
     * token is working
     */
    public CoreTokensRecord setIsRevoked(Byte value) {
        set(9, value);
        return this;
    }

    /**
     * Getter for <code>core.core_tokens.IS_REVOKED</code>. If false, means
     * token is working
     */
    public Byte getIsRevoked() {
        return (Byte) get(9);
    }

    /**
     * Setter for <code>core.core_tokens.EXPIRES_AT</code>. Time when this token
     * will expire
     */
    public CoreTokensRecord setExpiresAt(LocalDateTime value) {
        set(10, value);
        return this;
    }

    /**
     * Getter for <code>core.core_tokens.EXPIRES_AT</code>. Time when this token
     * will expire
     */
    public LocalDateTime getExpiresAt() {
        return (LocalDateTime) get(10);
    }

    /**
     * Setter for <code>core.core_tokens.IS_LIFETIME_TOKEN</code>. If true,
     * token will not have expiry
     */
    public CoreTokensRecord setIsLifetimeToken(Byte value) {
        set(11, value);
        return this;
    }

    /**
     * Getter for <code>core.core_tokens.IS_LIFETIME_TOKEN</code>. If true,
     * token will not have expiry
     */
    public Byte getIsLifetimeToken() {
        return (Byte) get(11);
    }

    /**
     * Setter for <code>core.core_tokens.CREATED_BY</code>. ID of the user who
     * created this row
     */
    public CoreTokensRecord setCreatedBy(ULong value) {
        set(12, value);
        return this;
    }

    /**
     * Getter for <code>core.core_tokens.CREATED_BY</code>. ID of the user who
     * created this row
     */
    public ULong getCreatedBy() {
        return (ULong) get(12);
    }

    /**
     * Setter for <code>core.core_tokens.UPDATED_BY</code>. ID of the user who
     * updated this row
     */
    public CoreTokensRecord setUpdatedBy(ULong value) {
        set(13, value);
        return this;
    }

    /**
     * Getter for <code>core.core_tokens.UPDATED_BY</code>. ID of the user who
     * updated this row
     */
    public ULong getUpdatedBy() {
        return (ULong) get(13);
    }

    /**
     * Setter for <code>core.core_tokens.CREATED_AT</code>. Time when this row
     * is created
     */
    public CoreTokensRecord setCreatedAt(LocalDateTime value) {
        set(14, value);
        return this;
    }

    /**
     * Getter for <code>core.core_tokens.CREATED_AT</code>. Time when this row
     * is created
     */
    public LocalDateTime getCreatedAt() {
        return (LocalDateTime) get(14);
    }

    /**
     * Setter for <code>core.core_tokens.UPDATED_AT</code>. Time when this row
     * is updated
     */
    public CoreTokensRecord setUpdatedAt(LocalDateTime value) {
        set(15, value);
        return this;
    }

    /**
     * Getter for <code>core.core_tokens.UPDATED_AT</code>. Time when this row
     * is updated
     */
    public LocalDateTime getUpdatedAt() {
        return (LocalDateTime) get(15);
    }

    // -------------------------------------------------------------------------
    // Primary key information
    // -------------------------------------------------------------------------

    @Override
    public Record1<ULong> key() {
        return (Record1) super.key();
    }

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /**
     * Create a detached CoreTokensRecord
     */
    public CoreTokensRecord() {
        super(CoreTokens.CORE_TOKENS);
    }

    /**
     * Create a detached, initialised CoreTokensRecord
     */
    public CoreTokensRecord(ULong id, ULong userId, String clientCode, String appCode, String state, String connectionName, CoreTokensTokenType tokenType, String token, JSON tokenMetadata, Byte isRevoked, LocalDateTime expiresAt, Byte isLifetimeToken, ULong createdBy, ULong updatedBy, LocalDateTime createdAt, LocalDateTime updatedAt) {
        super(CoreTokens.CORE_TOKENS);

        setId(id);
        setUserId(userId);
        setClientCode(clientCode);
        setAppCode(appCode);
        setState(state);
        setConnectionName(connectionName);
        setTokenType(tokenType);
        setToken(token);
        setTokenMetadata(tokenMetadata);
        setIsRevoked(isRevoked);
        setExpiresAt(expiresAt);
        setIsLifetimeToken(isLifetimeToken);
        setCreatedBy(createdBy);
        setUpdatedBy(updatedBy);
        setCreatedAt(createdAt);
        setUpdatedAt(updatedAt);
        resetChangedOnNotNull();
    }
}
