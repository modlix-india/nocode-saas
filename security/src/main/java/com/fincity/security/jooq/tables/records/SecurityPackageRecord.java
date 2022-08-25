/*
 * This file is generated by jOOQ.
 */
package com.fincity.security.jooq.tables.records;


import com.fincity.security.jooq.tables.SecurityPackage;

import java.time.LocalDateTime;

import org.jooq.Field;
import org.jooq.Record1;
import org.jooq.Record10;
import org.jooq.Row10;
import org.jooq.impl.UpdatableRecordImpl;
import org.jooq.types.ULong;


/**
 * This class is generated by jOOQ.
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes" })
public class SecurityPackageRecord extends UpdatableRecordImpl<SecurityPackageRecord> implements Record10<ULong, ULong, String, String, String, Byte, ULong, LocalDateTime, ULong, LocalDateTime> {

    private static final long serialVersionUID = 1L;

    /**
     * Setter for <code>security.security_package.ID</code>. Primary key
     */
    public SecurityPackageRecord setId(ULong value) {
        set(0, value);
        return this;
    }

    /**
     * Getter for <code>security.security_package.ID</code>. Primary key
     */
    public ULong getId() {
        return (ULong) get(0);
    }

    /**
     * Setter for <code>security.security_package.CLIENT_ID</code>. Client ID
     * for which this permission belongs to
     */
    public SecurityPackageRecord setClientId(ULong value) {
        set(1, value);
        return this;
    }

    /**
     * Getter for <code>security.security_package.CLIENT_ID</code>. Client ID
     * for which this permission belongs to
     */
    public ULong getClientId() {
        return (ULong) get(1);
    }

    /**
     * Setter for <code>security.security_package.CODE</code>. Package code
     */
    public SecurityPackageRecord setCode(String value) {
        set(2, value);
        return this;
    }

    /**
     * Getter for <code>security.security_package.CODE</code>. Package code
     */
    public String getCode() {
        return (String) get(2);
    }

    /**
     * Setter for <code>security.security_package.NAME</code>. Name of the
     * package
     */
    public SecurityPackageRecord setName(String value) {
        set(3, value);
        return this;
    }

    /**
     * Getter for <code>security.security_package.NAME</code>. Name of the
     * package
     */
    public String getName() {
        return (String) get(3);
    }

    /**
     * Setter for <code>security.security_package.DESCRIPTION</code>.
     * Description of the package
     */
    public SecurityPackageRecord setDescription(String value) {
        set(4, value);
        return this;
    }

    /**
     * Getter for <code>security.security_package.DESCRIPTION</code>.
     * Description of the package
     */
    public String getDescription() {
        return (String) get(4);
    }

    /**
     * Setter for <code>security.security_package.BASE</code>. Indicator if this
     * package is for every client
     */
    public SecurityPackageRecord setBase(Byte value) {
        set(5, value);
        return this;
    }

    /**
     * Getter for <code>security.security_package.BASE</code>. Indicator if this
     * package is for every client
     */
    public Byte getBase() {
        return (Byte) get(5);
    }

    /**
     * Setter for <code>security.security_package.CREATED_BY</code>. ID of the
     * user who created this row
     */
    public SecurityPackageRecord setCreatedBy(ULong value) {
        set(6, value);
        return this;
    }

    /**
     * Getter for <code>security.security_package.CREATED_BY</code>. ID of the
     * user who created this row
     */
    public ULong getCreatedBy() {
        return (ULong) get(6);
    }

    /**
     * Setter for <code>security.security_package.CREATED_AT</code>. Time when
     * this row is created
     */
    public SecurityPackageRecord setCreatedAt(LocalDateTime value) {
        set(7, value);
        return this;
    }

    /**
     * Getter for <code>security.security_package.CREATED_AT</code>. Time when
     * this row is created
     */
    public LocalDateTime getCreatedAt() {
        return (LocalDateTime) get(7);
    }

    /**
     * Setter for <code>security.security_package.UPDATED_BY</code>. ID of the
     * user who updated this row
     */
    public SecurityPackageRecord setUpdatedBy(ULong value) {
        set(8, value);
        return this;
    }

    /**
     * Getter for <code>security.security_package.UPDATED_BY</code>. ID of the
     * user who updated this row
     */
    public ULong getUpdatedBy() {
        return (ULong) get(8);
    }

    /**
     * Setter for <code>security.security_package.UPDATED_AT</code>. Time when
     * this row is updated
     */
    public SecurityPackageRecord setUpdatedAt(LocalDateTime value) {
        set(9, value);
        return this;
    }

    /**
     * Getter for <code>security.security_package.UPDATED_AT</code>. Time when
     * this row is updated
     */
    public LocalDateTime getUpdatedAt() {
        return (LocalDateTime) get(9);
    }

    // -------------------------------------------------------------------------
    // Primary key information
    // -------------------------------------------------------------------------

    @Override
    public Record1<ULong> key() {
        return (Record1) super.key();
    }

    // -------------------------------------------------------------------------
    // Record10 type implementation
    // -------------------------------------------------------------------------

    @Override
    public Row10<ULong, ULong, String, String, String, Byte, ULong, LocalDateTime, ULong, LocalDateTime> fieldsRow() {
        return (Row10) super.fieldsRow();
    }

    @Override
    public Row10<ULong, ULong, String, String, String, Byte, ULong, LocalDateTime, ULong, LocalDateTime> valuesRow() {
        return (Row10) super.valuesRow();
    }

    @Override
    public Field<ULong> field1() {
        return SecurityPackage.SECURITY_PACKAGE.ID;
    }

    @Override
    public Field<ULong> field2() {
        return SecurityPackage.SECURITY_PACKAGE.CLIENT_ID;
    }

    @Override
    public Field<String> field3() {
        return SecurityPackage.SECURITY_PACKAGE.CODE;
    }

    @Override
    public Field<String> field4() {
        return SecurityPackage.SECURITY_PACKAGE.NAME;
    }

    @Override
    public Field<String> field5() {
        return SecurityPackage.SECURITY_PACKAGE.DESCRIPTION;
    }

    @Override
    public Field<Byte> field6() {
        return SecurityPackage.SECURITY_PACKAGE.BASE;
    }

    @Override
    public Field<ULong> field7() {
        return SecurityPackage.SECURITY_PACKAGE.CREATED_BY;
    }

    @Override
    public Field<LocalDateTime> field8() {
        return SecurityPackage.SECURITY_PACKAGE.CREATED_AT;
    }

    @Override
    public Field<ULong> field9() {
        return SecurityPackage.SECURITY_PACKAGE.UPDATED_BY;
    }

    @Override
    public Field<LocalDateTime> field10() {
        return SecurityPackage.SECURITY_PACKAGE.UPDATED_AT;
    }

    @Override
    public ULong component1() {
        return getId();
    }

    @Override
    public ULong component2() {
        return getClientId();
    }

    @Override
    public String component3() {
        return getCode();
    }

    @Override
    public String component4() {
        return getName();
    }

    @Override
    public String component5() {
        return getDescription();
    }

    @Override
    public Byte component6() {
        return getBase();
    }

    @Override
    public ULong component7() {
        return getCreatedBy();
    }

    @Override
    public LocalDateTime component8() {
        return getCreatedAt();
    }

    @Override
    public ULong component9() {
        return getUpdatedBy();
    }

    @Override
    public LocalDateTime component10() {
        return getUpdatedAt();
    }

    @Override
    public ULong value1() {
        return getId();
    }

    @Override
    public ULong value2() {
        return getClientId();
    }

    @Override
    public String value3() {
        return getCode();
    }

    @Override
    public String value4() {
        return getName();
    }

    @Override
    public String value5() {
        return getDescription();
    }

    @Override
    public Byte value6() {
        return getBase();
    }

    @Override
    public ULong value7() {
        return getCreatedBy();
    }

    @Override
    public LocalDateTime value8() {
        return getCreatedAt();
    }

    @Override
    public ULong value9() {
        return getUpdatedBy();
    }

    @Override
    public LocalDateTime value10() {
        return getUpdatedAt();
    }

    @Override
    public SecurityPackageRecord value1(ULong value) {
        setId(value);
        return this;
    }

    @Override
    public SecurityPackageRecord value2(ULong value) {
        setClientId(value);
        return this;
    }

    @Override
    public SecurityPackageRecord value3(String value) {
        setCode(value);
        return this;
    }

    @Override
    public SecurityPackageRecord value4(String value) {
        setName(value);
        return this;
    }

    @Override
    public SecurityPackageRecord value5(String value) {
        setDescription(value);
        return this;
    }

    @Override
    public SecurityPackageRecord value6(Byte value) {
        setBase(value);
        return this;
    }

    @Override
    public SecurityPackageRecord value7(ULong value) {
        setCreatedBy(value);
        return this;
    }

    @Override
    public SecurityPackageRecord value8(LocalDateTime value) {
        setCreatedAt(value);
        return this;
    }

    @Override
    public SecurityPackageRecord value9(ULong value) {
        setUpdatedBy(value);
        return this;
    }

    @Override
    public SecurityPackageRecord value10(LocalDateTime value) {
        setUpdatedAt(value);
        return this;
    }

    @Override
    public SecurityPackageRecord values(ULong value1, ULong value2, String value3, String value4, String value5, Byte value6, ULong value7, LocalDateTime value8, ULong value9, LocalDateTime value10) {
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
        return this;
    }

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /**
     * Create a detached SecurityPackageRecord
     */
    public SecurityPackageRecord() {
        super(SecurityPackage.SECURITY_PACKAGE);
    }

    /**
     * Create a detached, initialised SecurityPackageRecord
     */
    public SecurityPackageRecord(ULong id, ULong clientId, String code, String name, String description, Byte base, ULong createdBy, LocalDateTime createdAt, ULong updatedBy, LocalDateTime updatedAt) {
        super(SecurityPackage.SECURITY_PACKAGE);

        setId(id);
        setClientId(clientId);
        setCode(code);
        setName(name);
        setDescription(description);
        setBase(base);
        setCreatedBy(createdBy);
        setCreatedAt(createdAt);
        setUpdatedBy(updatedBy);
        setUpdatedAt(updatedAt);
    }
}
