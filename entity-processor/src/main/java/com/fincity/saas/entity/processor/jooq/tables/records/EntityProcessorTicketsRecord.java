/*
 * This file is generated by jOOQ.
 */
package com.fincity.saas.entity.processor.jooq.tables.records;


import com.fincity.saas.entity.processor.jooq.tables.EntityProcessorTickets;

import java.time.LocalDateTime;

import org.jooq.Record1;
import org.jooq.impl.UpdatableRecordImpl;
import org.jooq.types.ULong;


/**
 * This class is generated by jOOQ.
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes", "this-escape" })
public class EntityProcessorTicketsRecord extends UpdatableRecordImpl<EntityProcessorTicketsRecord> {

    private static final long serialVersionUID = 1L;

    /**
     * Setter for <code>entity_processor.entity_processor_tickets.ID</code>.
     * Primary key.
     */
    public EntityProcessorTicketsRecord setId(ULong value) {
        set(0, value);
        return this;
    }

    /**
     * Getter for <code>entity_processor.entity_processor_tickets.ID</code>.
     * Primary key.
     */
    public ULong getId() {
        return (ULong) get(0);
    }

    /**
     * Setter for
     * <code>entity_processor.entity_processor_tickets.APP_CODE</code>. App Code
     * on which this notification was sent.
     */
    public EntityProcessorTicketsRecord setAppCode(String value) {
        set(1, value);
        return this;
    }

    /**
     * Getter for
     * <code>entity_processor.entity_processor_tickets.APP_CODE</code>. App Code
     * on which this notification was sent.
     */
    public String getAppCode() {
        return (String) get(1);
    }

    /**
     * Setter for
     * <code>entity_processor.entity_processor_tickets.CLIENT_CODE</code>.
     * Client Code to whom this notification we sent.
     */
    public EntityProcessorTicketsRecord setClientCode(String value) {
        set(2, value);
        return this;
    }

    /**
     * Getter for
     * <code>entity_processor.entity_processor_tickets.CLIENT_CODE</code>.
     * Client Code to whom this notification we sent.
     */
    public String getClientCode() {
        return (String) get(2);
    }

    /**
     * Setter for <code>entity_processor.entity_processor_tickets.CODE</code>.
     * Unique Code to identify this row.
     */
    public EntityProcessorTicketsRecord setCode(String value) {
        set(3, value);
        return this;
    }

    /**
     * Getter for <code>entity_processor.entity_processor_tickets.CODE</code>.
     * Unique Code to identify this row.
     */
    public String getCode() {
        return (String) get(3);
    }

    /**
     * Setter for <code>entity_processor.entity_processor_tickets.NAME</code>.
     * Name of the Ticket. Ticket can be anything which will have a single
     * owner. For Example, Opportunity is a ticket of Lead , Task is a ticket of
     * Epic, Lead is ticket of Account.
     */
    public EntityProcessorTicketsRecord setName(String value) {
        set(4, value);
        return this;
    }

    /**
     * Getter for <code>entity_processor.entity_processor_tickets.NAME</code>.
     * Name of the Ticket. Ticket can be anything which will have a single
     * owner. For Example, Opportunity is a ticket of Lead , Task is a ticket of
     * Epic, Lead is ticket of Account.
     */
    public String getName() {
        return (String) get(4);
    }

    /**
     * Setter for
     * <code>entity_processor.entity_processor_tickets.DESCRIPTION</code>.
     * Description for the ticket.
     */
    public EntityProcessorTicketsRecord setDescription(String value) {
        set(5, value);
        return this;
    }

    /**
     * Getter for
     * <code>entity_processor.entity_processor_tickets.DESCRIPTION</code>.
     * Description for the ticket.
     */
    public String getDescription() {
        return (String) get(5);
    }

    /**
     * Setter for
     * <code>entity_processor.entity_processor_tickets.VERSION</code>. Version
     * of this row.
     */
    public EntityProcessorTicketsRecord setVersion(ULong value) {
        set(6, value);
        return this;
    }

    /**
     * Getter for
     * <code>entity_processor.entity_processor_tickets.VERSION</code>. Version
     * of this row.
     */
    public ULong getVersion() {
        return (ULong) get(6);
    }

    /**
     * Setter for
     * <code>entity_processor.entity_processor_tickets.OWNER_ID</code>. Owner
     * related to this ticket.
     */
    public EntityProcessorTicketsRecord setOwnerId(ULong value) {
        set(7, value);
        return this;
    }

    /**
     * Getter for
     * <code>entity_processor.entity_processor_tickets.OWNER_ID</code>. Owner
     * related to this ticket.
     */
    public ULong getOwnerId() {
        return (ULong) get(7);
    }

    /**
     * Setter for
     * <code>entity_processor.entity_processor_tickets.ASSIGNED_USER_ID</code>.
     * User which added this ticket or user who is assigned to this ticket.
     */
    public EntityProcessorTicketsRecord setAssignedUserId(ULong value) {
        set(8, value);
        return this;
    }

    /**
     * Getter for
     * <code>entity_processor.entity_processor_tickets.ASSIGNED_USER_ID</code>.
     * User which added this ticket or user who is assigned to this ticket.
     */
    public ULong getAssignedUserId() {
        return (ULong) get(8);
    }

    /**
     * Setter for
     * <code>entity_processor.entity_processor_tickets.DIAL_CODE</code>. Dial
     * code of the phone number this owner has.
     */
    public EntityProcessorTicketsRecord setDialCode(Short value) {
        set(9, value);
        return this;
    }

    /**
     * Getter for
     * <code>entity_processor.entity_processor_tickets.DIAL_CODE</code>. Dial
     * code of the phone number this owner has.
     */
    public Short getDialCode() {
        return (Short) get(9);
    }

    /**
     * Setter for
     * <code>entity_processor.entity_processor_tickets.PHONE_NUMBER</code>.
     * Phone number related to this owner.
     */
    public EntityProcessorTicketsRecord setPhoneNumber(String value) {
        set(10, value);
        return this;
    }

    /**
     * Getter for
     * <code>entity_processor.entity_processor_tickets.PHONE_NUMBER</code>.
     * Phone number related to this owner.
     */
    public String getPhoneNumber() {
        return (String) get(10);
    }

    /**
     * Setter for <code>entity_processor.entity_processor_tickets.EMAIL</code>.
     * Email related to this ticket.
     */
    public EntityProcessorTicketsRecord setEmail(String value) {
        set(11, value);
        return this;
    }

    /**
     * Getter for <code>entity_processor.entity_processor_tickets.EMAIL</code>.
     * Email related to this ticket.
     */
    public String getEmail() {
        return (String) get(11);
    }

    /**
     * Setter for
     * <code>entity_processor.entity_processor_tickets.PRODUCT_ID</code>.
     * Product related to this ticket.
     */
    public EntityProcessorTicketsRecord setProductId(ULong value) {
        set(12, value);
        return this;
    }

    /**
     * Getter for
     * <code>entity_processor.entity_processor_tickets.PRODUCT_ID</code>.
     * Product related to this ticket.
     */
    public ULong getProductId() {
        return (ULong) get(12);
    }

    /**
     * Setter for <code>entity_processor.entity_processor_tickets.STAGE</code>.
     * Status for this ticket.
     */
    public EntityProcessorTicketsRecord setStage(ULong value) {
        set(13, value);
        return this;
    }

    /**
     * Getter for <code>entity_processor.entity_processor_tickets.STAGE</code>.
     * Status for this ticket.
     */
    public ULong getStage() {
        return (ULong) get(13);
    }

    /**
     * Setter for <code>entity_processor.entity_processor_tickets.STATUS</code>.
     * Sub Status for this ticket.
     */
    public EntityProcessorTicketsRecord setStatus(ULong value) {
        set(14, value);
        return this;
    }

    /**
     * Getter for <code>entity_processor.entity_processor_tickets.STATUS</code>.
     * Sub Status for this ticket.
     */
    public ULong getStatus() {
        return (ULong) get(14);
    }

    /**
     * Setter for <code>entity_processor.entity_processor_tickets.SOURCE</code>.
     * Name of source form where we get this ticket.
     */
    public EntityProcessorTicketsRecord setSource(String value) {
        set(15, value);
        return this;
    }

    /**
     * Getter for <code>entity_processor.entity_processor_tickets.SOURCE</code>.
     * Name of source form where we get this ticket.
     */
    public String getSource() {
        return (String) get(15);
    }

    /**
     * Setter for
     * <code>entity_processor.entity_processor_tickets.SUB_SOURCE</code>. Name
     * of sub source of source form where we get this ticket.
     */
    public EntityProcessorTicketsRecord setSubSource(String value) {
        set(16, value);
        return this;
    }

    /**
     * Getter for
     * <code>entity_processor.entity_processor_tickets.SUB_SOURCE</code>. Name
     * of sub source of source form where we get this ticket.
     */
    public String getSubSource() {
        return (String) get(16);
    }

    /**
     * Setter for
     * <code>entity_processor.entity_processor_tickets.TEMP_ACTIVE</code>.
     * Temporary active flag for this product.
     */
    public EntityProcessorTicketsRecord setTempActive(Byte value) {
        set(17, value);
        return this;
    }

    /**
     * Getter for
     * <code>entity_processor.entity_processor_tickets.TEMP_ACTIVE</code>.
     * Temporary active flag for this product.
     */
    public Byte getTempActive() {
        return (Byte) get(17);
    }

    /**
     * Setter for
     * <code>entity_processor.entity_processor_tickets.IS_ACTIVE</code>. Flag to
     * check if this product is active or not.
     */
    public EntityProcessorTicketsRecord setIsActive(Byte value) {
        set(18, value);
        return this;
    }

    /**
     * Getter for
     * <code>entity_processor.entity_processor_tickets.IS_ACTIVE</code>. Flag to
     * check if this product is active or not.
     */
    public Byte getIsActive() {
        return (Byte) get(18);
    }

    /**
     * Setter for
     * <code>entity_processor.entity_processor_tickets.CREATED_BY</code>. ID of
     * the user who created this row.
     */
    public EntityProcessorTicketsRecord setCreatedBy(ULong value) {
        set(19, value);
        return this;
    }

    /**
     * Getter for
     * <code>entity_processor.entity_processor_tickets.CREATED_BY</code>. ID of
     * the user who created this row.
     */
    public ULong getCreatedBy() {
        return (ULong) get(19);
    }

    /**
     * Setter for
     * <code>entity_processor.entity_processor_tickets.CREATED_AT</code>. Time
     * when this row is created.
     */
    public EntityProcessorTicketsRecord setCreatedAt(LocalDateTime value) {
        set(20, value);
        return this;
    }

    /**
     * Getter for
     * <code>entity_processor.entity_processor_tickets.CREATED_AT</code>. Time
     * when this row is created.
     */
    public LocalDateTime getCreatedAt() {
        return (LocalDateTime) get(20);
    }

    /**
     * Setter for
     * <code>entity_processor.entity_processor_tickets.UPDATED_BY</code>. ID of
     * the user who updated this row.
     */
    public EntityProcessorTicketsRecord setUpdatedBy(ULong value) {
        set(21, value);
        return this;
    }

    /**
     * Getter for
     * <code>entity_processor.entity_processor_tickets.UPDATED_BY</code>. ID of
     * the user who updated this row.
     */
    public ULong getUpdatedBy() {
        return (ULong) get(21);
    }

    /**
     * Setter for
     * <code>entity_processor.entity_processor_tickets.UPDATED_AT</code>. Time
     * when this row is updated.
     */
    public EntityProcessorTicketsRecord setUpdatedAt(LocalDateTime value) {
        set(22, value);
        return this;
    }

    /**
     * Getter for
     * <code>entity_processor.entity_processor_tickets.UPDATED_AT</code>. Time
     * when this row is updated.
     */
    public LocalDateTime getUpdatedAt() {
        return (LocalDateTime) get(22);
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
     * Create a detached EntityProcessorTicketsRecord
     */
    public EntityProcessorTicketsRecord() {
        super(EntityProcessorTickets.ENTITY_PROCESSOR_TICKETS);
    }

    /**
     * Create a detached, initialised EntityProcessorTicketsRecord
     */
    public EntityProcessorTicketsRecord(ULong id, String appCode, String clientCode, String code, String name, String description, ULong version, ULong ownerId, ULong assignedUserId, Short dialCode, String phoneNumber, String email, ULong productId, ULong stage, ULong status, String source, String subSource, Byte tempActive, Byte isActive, ULong createdBy, LocalDateTime createdAt, ULong updatedBy, LocalDateTime updatedAt) {
        super(EntityProcessorTickets.ENTITY_PROCESSOR_TICKETS);

        setId(id);
        setAppCode(appCode);
        setClientCode(clientCode);
        setCode(code);
        setName(name);
        setDescription(description);
        setVersion(version);
        setOwnerId(ownerId);
        setAssignedUserId(assignedUserId);
        setDialCode(dialCode);
        setPhoneNumber(phoneNumber);
        setEmail(email);
        setProductId(productId);
        setStage(stage);
        setStatus(status);
        setSource(source);
        setSubSource(subSource);
        setTempActive(tempActive);
        setIsActive(isActive);
        setCreatedBy(createdBy);
        setCreatedAt(createdAt);
        setUpdatedBy(updatedBy);
        setUpdatedAt(updatedAt);
        resetTouchedOnNotNull();
    }
}
