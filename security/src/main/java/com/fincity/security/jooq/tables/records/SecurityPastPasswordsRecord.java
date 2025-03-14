/*
 * This file is generated by jOOQ.
 */
package com.fincity.security.jooq.tables.records;


import com.fincity.security.jooq.tables.SecurityPastPasswords;

import java.time.LocalDateTime;

import org.jooq.Record1;
import org.jooq.impl.UpdatableRecordImpl;
import org.jooq.types.ULong;


/**
 * This class is generated by jOOQ.
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes", "this-escape" })
public class SecurityPastPasswordsRecord extends UpdatableRecordImpl<SecurityPastPasswordsRecord> {

    private static final long serialVersionUID = 1L;

    /**
     * Setter for <code>security.security_past_passwords.ID</code>. Primary key
     */
    public SecurityPastPasswordsRecord setId(ULong value) {
        set(0, value);
        return this;
    }

    /**
     * Getter for <code>security.security_past_passwords.ID</code>. Primary key
     */
    public ULong getId() {
        return (ULong) get(0);
    }

    /**
     * Setter for <code>security.security_past_passwords.USER_ID</code>. User ID
     */
    public SecurityPastPasswordsRecord setUserId(ULong value) {
        set(1, value);
        return this;
    }

    /**
     * Getter for <code>security.security_past_passwords.USER_ID</code>. User ID
     */
    public ULong getUserId() {
        return (ULong) get(1);
    }

    /**
     * Setter for <code>security.security_past_passwords.PASSWORD</code>.
     * Password message digested string
     */
    public SecurityPastPasswordsRecord setPassword(String value) {
        set(2, value);
        return this;
    }

    /**
     * Getter for <code>security.security_past_passwords.PASSWORD</code>.
     * Password message digested string
     */
    public String getPassword() {
        return (String) get(2);
    }

    /**
     * Setter for <code>security.security_past_passwords.PASSWORD_HASHED</code>.
     * Password stored is hashed or not
     */
    public SecurityPastPasswordsRecord setPasswordHashed(Byte value) {
        set(3, value);
        return this;
    }

    /**
     * Getter for <code>security.security_past_passwords.PASSWORD_HASHED</code>.
     * Password stored is hashed or not
     */
    public Byte getPasswordHashed() {
        return (Byte) get(3);
    }

    /**
     * Setter for <code>security.security_past_passwords.CREATED_BY</code>. ID
     * of the user who created this row
     */
    public SecurityPastPasswordsRecord setCreatedBy(ULong value) {
        set(4, value);
        return this;
    }

    /**
     * Getter for <code>security.security_past_passwords.CREATED_BY</code>. ID
     * of the user who created this row
     */
    public ULong getCreatedBy() {
        return (ULong) get(4);
    }

    /**
     * Setter for <code>security.security_past_passwords.CREATED_AT</code>. Time
     * when this row is created
     */
    public SecurityPastPasswordsRecord setCreatedAt(LocalDateTime value) {
        set(5, value);
        return this;
    }

    /**
     * Getter for <code>security.security_past_passwords.CREATED_AT</code>. Time
     * when this row is created
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
    // Constructors
    // -------------------------------------------------------------------------

    /**
     * Create a detached SecurityPastPasswordsRecord
     */
    public SecurityPastPasswordsRecord() {
        super(SecurityPastPasswords.SECURITY_PAST_PASSWORDS);
    }

    /**
     * Create a detached, initialised SecurityPastPasswordsRecord
     */
    public SecurityPastPasswordsRecord(ULong id, ULong userId, String password, Byte passwordHashed, ULong createdBy, LocalDateTime createdAt) {
        super(SecurityPastPasswords.SECURITY_PAST_PASSWORDS);

        setId(id);
        setUserId(userId);
        setPassword(password);
        setPasswordHashed(passwordHashed);
        setCreatedBy(createdBy);
        setCreatedAt(createdAt);
        resetChangedOnNotNull();
    }
}
