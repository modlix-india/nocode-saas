/*
 * This file is generated by jOOQ.
 */
package com.fincity.saas.entity.processor.jooq.tables.records;


import com.fincity.saas.entity.processor.jooq.tables.EntityProcessorTaskTypes;

import java.time.LocalDateTime;

import org.jooq.Record1;
import org.jooq.impl.UpdatableRecordImpl;
import org.jooq.types.ULong;


/**
 * This class is generated by jOOQ.
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes", "this-escape" })
public class EntityProcessorTaskTypesRecord extends UpdatableRecordImpl<EntityProcessorTaskTypesRecord> {

    private static final long serialVersionUID = 1L;

    /**
     * Setter for <code>entity_processor.entity_processor_task_types.ID</code>.
     * Primary key.
     */
    public EntityProcessorTaskTypesRecord setId(ULong value) {
        set(0, value);
        return this;
    }

    /**
     * Getter for <code>entity_processor.entity_processor_task_types.ID</code>.
     * Primary key.
     */
    public ULong getId() {
        return (ULong) get(0);
    }

    /**
     * Setter for
     * <code>entity_processor.entity_processor_task_types.APP_CODE</code>. App
     * Code on which this task type was created.
     */
    public EntityProcessorTaskTypesRecord setAppCode(String value) {
        set(1, value);
        return this;
    }

    /**
     * Getter for
     * <code>entity_processor.entity_processor_task_types.APP_CODE</code>. App
     * Code on which this task type was created.
     */
    public String getAppCode() {
        return (String) get(1);
    }

    /**
     * Setter for
     * <code>entity_processor.entity_processor_task_types.CLIENT_CODE</code>.
     * Client Code who created this task type.
     */
    public EntityProcessorTaskTypesRecord setClientCode(String value) {
        set(2, value);
        return this;
    }

    /**
     * Getter for
     * <code>entity_processor.entity_processor_task_types.CLIENT_CODE</code>.
     * Client Code who created this task type.
     */
    public String getClientCode() {
        return (String) get(2);
    }

    /**
     * Setter for
     * <code>entity_processor.entity_processor_task_types.CODE</code>. Unique
     * Code to identify this row.
     */
    public EntityProcessorTaskTypesRecord setCode(String value) {
        set(3, value);
        return this;
    }

    /**
     * Getter for
     * <code>entity_processor.entity_processor_task_types.CODE</code>. Unique
     * Code to identify this row.
     */
    public String getCode() {
        return (String) get(3);
    }

    /**
     * Setter for
     * <code>entity_processor.entity_processor_task_types.NAME</code>. Name of
     * the Task Type.
     */
    public EntityProcessorTaskTypesRecord setName(String value) {
        set(4, value);
        return this;
    }

    /**
     * Getter for
     * <code>entity_processor.entity_processor_task_types.NAME</code>. Name of
     * the Task Type.
     */
    public String getName() {
        return (String) get(4);
    }

    /**
     * Setter for
     * <code>entity_processor.entity_processor_task_types.DESCRIPTION</code>.
     * Description for the Task Type.
     */
    public EntityProcessorTaskTypesRecord setDescription(String value) {
        set(5, value);
        return this;
    }

    /**
     * Getter for
     * <code>entity_processor.entity_processor_task_types.DESCRIPTION</code>.
     * Description for the Task Type.
     */
    public String getDescription() {
        return (String) get(5);
    }

    /**
     * Setter for
     * <code>entity_processor.entity_processor_task_types.TEMP_ACTIVE</code>.
     * Temporary active flag for this task type.
     */
    public EntityProcessorTaskTypesRecord setTempActive(Byte value) {
        set(6, value);
        return this;
    }

    /**
     * Getter for
     * <code>entity_processor.entity_processor_task_types.TEMP_ACTIVE</code>.
     * Temporary active flag for this task type.
     */
    public Byte getTempActive() {
        return (Byte) get(6);
    }

    /**
     * Setter for
     * <code>entity_processor.entity_processor_task_types.IS_ACTIVE</code>. Flag
     * to check if this task type is active or not.
     */
    public EntityProcessorTaskTypesRecord setIsActive(Byte value) {
        set(7, value);
        return this;
    }

    /**
     * Getter for
     * <code>entity_processor.entity_processor_task_types.IS_ACTIVE</code>. Flag
     * to check if this task type is active or not.
     */
    public Byte getIsActive() {
        return (Byte) get(7);
    }

    /**
     * Setter for
     * <code>entity_processor.entity_processor_task_types.CREATED_BY</code>. ID
     * of the user who created this row.
     */
    public EntityProcessorTaskTypesRecord setCreatedBy(ULong value) {
        set(8, value);
        return this;
    }

    /**
     * Getter for
     * <code>entity_processor.entity_processor_task_types.CREATED_BY</code>. ID
     * of the user who created this row.
     */
    public ULong getCreatedBy() {
        return (ULong) get(8);
    }

    /**
     * Setter for
     * <code>entity_processor.entity_processor_task_types.CREATED_AT</code>.
     * Time when this row is created.
     */
    public EntityProcessorTaskTypesRecord setCreatedAt(LocalDateTime value) {
        set(9, value);
        return this;
    }

    /**
     * Getter for
     * <code>entity_processor.entity_processor_task_types.CREATED_AT</code>.
     * Time when this row is created.
     */
    public LocalDateTime getCreatedAt() {
        return (LocalDateTime) get(9);
    }

    /**
     * Setter for
     * <code>entity_processor.entity_processor_task_types.UPDATED_BY</code>. ID
     * of the user who updated this row.
     */
    public EntityProcessorTaskTypesRecord setUpdatedBy(ULong value) {
        set(10, value);
        return this;
    }

    /**
     * Getter for
     * <code>entity_processor.entity_processor_task_types.UPDATED_BY</code>. ID
     * of the user who updated this row.
     */
    public ULong getUpdatedBy() {
        return (ULong) get(10);
    }

    /**
     * Setter for
     * <code>entity_processor.entity_processor_task_types.UPDATED_AT</code>.
     * Time when this row is updated.
     */
    public EntityProcessorTaskTypesRecord setUpdatedAt(LocalDateTime value) {
        set(11, value);
        return this;
    }

    /**
     * Getter for
     * <code>entity_processor.entity_processor_task_types.UPDATED_AT</code>.
     * Time when this row is updated.
     */
    public LocalDateTime getUpdatedAt() {
        return (LocalDateTime) get(11);
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
     * Create a detached EntityProcessorTaskTypesRecord
     */
    public EntityProcessorTaskTypesRecord() {
        super(EntityProcessorTaskTypes.ENTITY_PROCESSOR_TASK_TYPES);
    }

    /**
     * Create a detached, initialised EntityProcessorTaskTypesRecord
     */
    public EntityProcessorTaskTypesRecord(ULong id, String appCode, String clientCode, String code, String name, String description, Byte tempActive, Byte isActive, ULong createdBy, LocalDateTime createdAt, ULong updatedBy, LocalDateTime updatedAt) {
        super(EntityProcessorTaskTypes.ENTITY_PROCESSOR_TASK_TYPES);

        setId(id);
        setAppCode(appCode);
        setClientCode(clientCode);
        setCode(code);
        setName(name);
        setDescription(description);
        setTempActive(tempActive);
        setIsActive(isActive);
        setCreatedBy(createdBy);
        setCreatedAt(createdAt);
        setUpdatedBy(updatedBy);
        setUpdatedAt(updatedAt);
        resetTouchedOnNotNull();
    }
}
