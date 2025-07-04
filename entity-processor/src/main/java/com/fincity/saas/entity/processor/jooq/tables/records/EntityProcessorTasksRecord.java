/*
 * This file is generated by jOOQ.
 */
package com.fincity.saas.entity.processor.jooq.tables.records;


import com.fincity.saas.entity.processor.enums.content.TaskPriority;
import com.fincity.saas.entity.processor.jooq.tables.EntityProcessorTasks;

import java.time.LocalDateTime;

import org.jooq.Record1;
import org.jooq.impl.UpdatableRecordImpl;
import org.jooq.types.ULong;


/**
 * This class is generated by jOOQ.
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes", "this-escape" })
public class EntityProcessorTasksRecord extends UpdatableRecordImpl<EntityProcessorTasksRecord> {

    private static final long serialVersionUID = 1L;

    /**
     * Setter for <code>entity_processor.entity_processor_tasks.ID</code>.
     * Primary key.
     */
    public EntityProcessorTasksRecord setId(ULong value) {
        set(0, value);
        return this;
    }

    /**
     * Getter for <code>entity_processor.entity_processor_tasks.ID</code>.
     * Primary key.
     */
    public ULong getId() {
        return (ULong) get(0);
    }

    /**
     * Setter for <code>entity_processor.entity_processor_tasks.APP_CODE</code>.
     * App Code on which this task was created.
     */
    public EntityProcessorTasksRecord setAppCode(String value) {
        set(1, value);
        return this;
    }

    /**
     * Getter for <code>entity_processor.entity_processor_tasks.APP_CODE</code>.
     * App Code on which this task was created.
     */
    public String getAppCode() {
        return (String) get(1);
    }

    /**
     * Setter for
     * <code>entity_processor.entity_processor_tasks.CLIENT_CODE</code>. Client
     * Code who created this task.
     */
    public EntityProcessorTasksRecord setClientCode(String value) {
        set(2, value);
        return this;
    }

    /**
     * Getter for
     * <code>entity_processor.entity_processor_tasks.CLIENT_CODE</code>. Client
     * Code who created this task.
     */
    public String getClientCode() {
        return (String) get(2);
    }

    /**
     * Setter for <code>entity_processor.entity_processor_tasks.CODE</code>.
     * Unique Code to identify this row.
     */
    public EntityProcessorTasksRecord setCode(String value) {
        set(3, value);
        return this;
    }

    /**
     * Getter for <code>entity_processor.entity_processor_tasks.CODE</code>.
     * Unique Code to identify this row.
     */
    public String getCode() {
        return (String) get(3);
    }

    /**
     * Setter for <code>entity_processor.entity_processor_tasks.NAME</code>.
     * Name of the Task.
     */
    public EntityProcessorTasksRecord setName(String value) {
        set(4, value);
        return this;
    }

    /**
     * Getter for <code>entity_processor.entity_processor_tasks.NAME</code>.
     * Name of the Task.
     */
    public String getName() {
        return (String) get(4);
    }

    /**
     * Setter for
     * <code>entity_processor.entity_processor_tasks.DESCRIPTION</code>.
     * Description for the Task.
     */
    public EntityProcessorTasksRecord setDescription(String value) {
        set(5, value);
        return this;
    }

    /**
     * Getter for
     * <code>entity_processor.entity_processor_tasks.DESCRIPTION</code>.
     * Description for the Task.
     */
    public String getDescription() {
        return (String) get(5);
    }

    /**
     * Setter for <code>entity_processor.entity_processor_tasks.VERSION</code>.
     * Version of this row.
     */
    public EntityProcessorTasksRecord setVersion(Integer value) {
        set(6, value);
        return this;
    }

    /**
     * Getter for <code>entity_processor.entity_processor_tasks.VERSION</code>.
     * Version of this row.
     */
    public Integer getVersion() {
        return (Integer) get(6);
    }

    /**
     * Setter for <code>entity_processor.entity_processor_tasks.CONTENT</code>.
     * Content of the task.
     */
    public EntityProcessorTasksRecord setContent(String value) {
        set(7, value);
        return this;
    }

    /**
     * Getter for <code>entity_processor.entity_processor_tasks.CONTENT</code>.
     * Content of the task.
     */
    public String getContent() {
        return (String) get(7);
    }

    /**
     * Setter for
     * <code>entity_processor.entity_processor_tasks.HAS_ATTACHMENT</code>.
     * Whether this task has attachments.
     */
    public EntityProcessorTasksRecord setHasAttachment(String value) {
        set(8, value);
        return this;
    }

    /**
     * Getter for
     * <code>entity_processor.entity_processor_tasks.HAS_ATTACHMENT</code>.
     * Whether this task has attachments.
     */
    public String getHasAttachment() {
        return (String) get(8);
    }

    /**
     * Setter for <code>entity_processor.entity_processor_tasks.OWNER_ID</code>.
     * Owner related to this task.
     */
    public EntityProcessorTasksRecord setOwnerId(ULong value) {
        set(9, value);
        return this;
    }

    /**
     * Getter for <code>entity_processor.entity_processor_tasks.OWNER_ID</code>.
     * Owner related to this task.
     */
    public ULong getOwnerId() {
        return (ULong) get(9);
    }

    /**
     * Setter for
     * <code>entity_processor.entity_processor_tasks.TICKET_ID</code>. Ticket
     * related to this task.
     */
    public EntityProcessorTasksRecord setTicketId(ULong value) {
        set(10, value);
        return this;
    }

    /**
     * Getter for
     * <code>entity_processor.entity_processor_tasks.TICKET_ID</code>. Ticket
     * related to this task.
     */
    public ULong getTicketId() {
        return (ULong) get(10);
    }

    /**
     * Setter for
     * <code>entity_processor.entity_processor_tasks.TASK_TYPE_ID</code>. Type
     * of the task.
     */
    public EntityProcessorTasksRecord setTaskTypeId(ULong value) {
        set(11, value);
        return this;
    }

    /**
     * Getter for
     * <code>entity_processor.entity_processor_tasks.TASK_TYPE_ID</code>. Type
     * of the task.
     */
    public ULong getTaskTypeId() {
        return (ULong) get(11);
    }

    /**
     * Setter for <code>entity_processor.entity_processor_tasks.DUE_DATE</code>.
     * Due date for this task.
     */
    public EntityProcessorTasksRecord setDueDate(LocalDateTime value) {
        set(12, value);
        return this;
    }

    /**
     * Getter for <code>entity_processor.entity_processor_tasks.DUE_DATE</code>.
     * Due date for this task.
     */
    public LocalDateTime getDueDate() {
        return (LocalDateTime) get(12);
    }

    /**
     * Setter for
     * <code>entity_processor.entity_processor_tasks.TASK_PRIORITY</code>.
     * Priority level of the task.
     */
    public EntityProcessorTasksRecord setTaskPriority(TaskPriority value) {
        set(13, value);
        return this;
    }

    /**
     * Getter for
     * <code>entity_processor.entity_processor_tasks.TASK_PRIORITY</code>.
     * Priority level of the task.
     */
    public TaskPriority getTaskPriority() {
        return (TaskPriority) get(13);
    }

    /**
     * Setter for
     * <code>entity_processor.entity_processor_tasks.IS_COMPLETED</code>. Flag
     * to tell, is the task Completed
     */
    public EntityProcessorTasksRecord setIsCompleted(Byte value) {
        set(14, value);
        return this;
    }

    /**
     * Getter for
     * <code>entity_processor.entity_processor_tasks.IS_COMPLETED</code>. Flag
     * to tell, is the task Completed
     */
    public Byte getIsCompleted() {
        return (Byte) get(14);
    }

    /**
     * Setter for
     * <code>entity_processor.entity_processor_tasks.COMPLETED_DATE</code>.
     * Timestamp when this task was Completed
     */
    public EntityProcessorTasksRecord setCompletedDate(LocalDateTime value) {
        set(15, value);
        return this;
    }

    /**
     * Getter for
     * <code>entity_processor.entity_processor_tasks.COMPLETED_DATE</code>.
     * Timestamp when this task was Completed
     */
    public LocalDateTime getCompletedDate() {
        return (LocalDateTime) get(15);
    }

    /**
     * Setter for
     * <code>entity_processor.entity_processor_tasks.IS_CANCELLED</code>. Flag
     * to tell, is the task Cancelled
     */
    public EntityProcessorTasksRecord setIsCancelled(Byte value) {
        set(16, value);
        return this;
    }

    /**
     * Getter for
     * <code>entity_processor.entity_processor_tasks.IS_CANCELLED</code>. Flag
     * to tell, is the task Cancelled
     */
    public Byte getIsCancelled() {
        return (Byte) get(16);
    }

    /**
     * Setter for
     * <code>entity_processor.entity_processor_tasks.CANCELLED_DATE</code>.
     * Timestamp when this task was Cancelled
     */
    public EntityProcessorTasksRecord setCancelledDate(LocalDateTime value) {
        set(17, value);
        return this;
    }

    /**
     * Getter for
     * <code>entity_processor.entity_processor_tasks.CANCELLED_DATE</code>.
     * Timestamp when this task was Cancelled
     */
    public LocalDateTime getCancelledDate() {
        return (LocalDateTime) get(17);
    }

    /**
     * Setter for
     * <code>entity_processor.entity_processor_tasks.IS_DELAYED</code>. Error
     * Message
     */
    public EntityProcessorTasksRecord setIsDelayed(Byte value) {
        set(18, value);
        return this;
    }

    /**
     * Getter for
     * <code>entity_processor.entity_processor_tasks.IS_DELAYED</code>. Error
     * Message
     */
    public Byte getIsDelayed() {
        return (Byte) get(18);
    }

    /**
     * Setter for
     * <code>entity_processor.entity_processor_tasks.HAS_REMINDER</code>.
     * Whether this task has a reminder set.
     */
    public EntityProcessorTasksRecord setHasReminder(Byte value) {
        set(19, value);
        return this;
    }

    /**
     * Getter for
     * <code>entity_processor.entity_processor_tasks.HAS_REMINDER</code>.
     * Whether this task has a reminder set.
     */
    public Byte getHasReminder() {
        return (Byte) get(19);
    }

    /**
     * Setter for
     * <code>entity_processor.entity_processor_tasks.NEXT_REMINDER</code>. Next
     * reminder date and time for this task.
     */
    public EntityProcessorTasksRecord setNextReminder(LocalDateTime value) {
        set(20, value);
        return this;
    }

    /**
     * Getter for
     * <code>entity_processor.entity_processor_tasks.NEXT_REMINDER</code>. Next
     * reminder date and time for this task.
     */
    public LocalDateTime getNextReminder() {
        return (LocalDateTime) get(20);
    }

    /**
     * Setter for
     * <code>entity_processor.entity_processor_tasks.TEMP_ACTIVE</code>.
     * Temporary active flag for this task.
     */
    public EntityProcessorTasksRecord setTempActive(Byte value) {
        set(21, value);
        return this;
    }

    /**
     * Getter for
     * <code>entity_processor.entity_processor_tasks.TEMP_ACTIVE</code>.
     * Temporary active flag for this task.
     */
    public Byte getTempActive() {
        return (Byte) get(21);
    }

    /**
     * Setter for
     * <code>entity_processor.entity_processor_tasks.IS_ACTIVE</code>. Flag to
     * check if this task is active or not.
     */
    public EntityProcessorTasksRecord setIsActive(Byte value) {
        set(22, value);
        return this;
    }

    /**
     * Getter for
     * <code>entity_processor.entity_processor_tasks.IS_ACTIVE</code>. Flag to
     * check if this task is active or not.
     */
    public Byte getIsActive() {
        return (Byte) get(22);
    }

    /**
     * Setter for
     * <code>entity_processor.entity_processor_tasks.CREATED_BY</code>. ID of
     * the user who created this row.
     */
    public EntityProcessorTasksRecord setCreatedBy(ULong value) {
        set(23, value);
        return this;
    }

    /**
     * Getter for
     * <code>entity_processor.entity_processor_tasks.CREATED_BY</code>. ID of
     * the user who created this row.
     */
    public ULong getCreatedBy() {
        return (ULong) get(23);
    }

    /**
     * Setter for
     * <code>entity_processor.entity_processor_tasks.CREATED_AT</code>. Time
     * when this row is created.
     */
    public EntityProcessorTasksRecord setCreatedAt(LocalDateTime value) {
        set(24, value);
        return this;
    }

    /**
     * Getter for
     * <code>entity_processor.entity_processor_tasks.CREATED_AT</code>. Time
     * when this row is created.
     */
    public LocalDateTime getCreatedAt() {
        return (LocalDateTime) get(24);
    }

    /**
     * Setter for
     * <code>entity_processor.entity_processor_tasks.UPDATED_BY</code>. ID of
     * the user who updated this row.
     */
    public EntityProcessorTasksRecord setUpdatedBy(ULong value) {
        set(25, value);
        return this;
    }

    /**
     * Getter for
     * <code>entity_processor.entity_processor_tasks.UPDATED_BY</code>. ID of
     * the user who updated this row.
     */
    public ULong getUpdatedBy() {
        return (ULong) get(25);
    }

    /**
     * Setter for
     * <code>entity_processor.entity_processor_tasks.UPDATED_AT</code>. Time
     * when this row is updated.
     */
    public EntityProcessorTasksRecord setUpdatedAt(LocalDateTime value) {
        set(26, value);
        return this;
    }

    /**
     * Getter for
     * <code>entity_processor.entity_processor_tasks.UPDATED_AT</code>. Time
     * when this row is updated.
     */
    public LocalDateTime getUpdatedAt() {
        return (LocalDateTime) get(26);
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
     * Create a detached EntityProcessorTasksRecord
     */
    public EntityProcessorTasksRecord() {
        super(EntityProcessorTasks.ENTITY_PROCESSOR_TASKS);
    }

    /**
     * Create a detached, initialised EntityProcessorTasksRecord
     */
    public EntityProcessorTasksRecord(ULong id, String appCode, String clientCode, String code, String name, String description, Integer version, String content, String hasAttachment, ULong ownerId, ULong ticketId, ULong taskTypeId, LocalDateTime dueDate, TaskPriority taskPriority, Byte isCompleted, LocalDateTime completedDate, Byte isCancelled, LocalDateTime cancelledDate, Byte isDelayed, Byte hasReminder, LocalDateTime nextReminder, Byte tempActive, Byte isActive, ULong createdBy, LocalDateTime createdAt, ULong updatedBy, LocalDateTime updatedAt) {
        super(EntityProcessorTasks.ENTITY_PROCESSOR_TASKS);

        setId(id);
        setAppCode(appCode);
        setClientCode(clientCode);
        setCode(code);
        setName(name);
        setDescription(description);
        setVersion(version);
        setContent(content);
        setHasAttachment(hasAttachment);
        setOwnerId(ownerId);
        setTicketId(ticketId);
        setTaskTypeId(taskTypeId);
        setDueDate(dueDate);
        setTaskPriority(taskPriority);
        setIsCompleted(isCompleted);
        setCompletedDate(completedDate);
        setIsCancelled(isCancelled);
        setCancelledDate(cancelledDate);
        setIsDelayed(isDelayed);
        setHasReminder(hasReminder);
        setNextReminder(nextReminder);
        setTempActive(tempActive);
        setIsActive(isActive);
        setCreatedBy(createdBy);
        setCreatedAt(createdAt);
        setUpdatedBy(updatedBy);
        setUpdatedAt(updatedAt);
        resetTouchedOnNotNull();
    }
}
