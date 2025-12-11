ALTER TABLE `entity_processor`.`entity_processor_tickets`
  ADD COLUMN `TAG` ENUM('HOT','WARM','COLD') NULL DEFAULT NULL COMMENT 'Deal Tag - HOT / WARM / COLD' AFTER `CAMPAIGN_ID`;

ALTER TABLE `entity_processor`.`entity_processor_activities`
    MODIFY COLUMN `ACTIVITY_ACTION` ENUM (
        'CREATE',
        'RE_INQUIRY',
        'QUALIFY',
        'DISQUALIFY',
        'DISCARD',
        'IMPORT',
        'STATUS_CREATE',
        'STAGE_UPDATE',
        'TAG_CREATE',
        'TAG_UPDATE',
        'WALK_IN',
        'TASK_CREATE',
        'TASK_UPDATE',
        'TASK_COMPLETE',
        'TASK_CANCELLED',
        'TASK_DELETE',
        'REMINDER_SET',
        'DOCUMENT_UPLOAD',
        'DOCUMENT_DOWNLOAD',
        'DOCUMENT_DELETE',
        'NOTE_ADD',
        'NOTE_UPDATE',
        'NOTE_DELETE',
        'ASSIGN',
        'REASSIGN',
        'UPDATE',
        'REASSIGN_SYSTEM',
        'OWNERSHIP_TRANSFER',
        'CALL_LOG',
        'WHATSAPP',
        'EMAIL_SENT',
        'SMS_SENT',
        'FIELD_UPDATE',
        'CUSTOM_FIELD_UPDATE',
        'LOCATION_UPDATE',
        'OTHER'
        )
        NOT NULL COMMENT 'Activity Action categories for this Activity.';
