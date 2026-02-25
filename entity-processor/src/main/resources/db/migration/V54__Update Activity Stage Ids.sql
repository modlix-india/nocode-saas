UPDATE entity_processor.entity_processor_activities a
    JOIN entity_processor.entity_processor_stages s
    ON s.ID = CAST(JSON_UNQUOTE(JSON_EXTRACT(a.OBJECT_DATA, '$.stage.id')) AS UNSIGNED)
SET a.`STAGE_ID` = s.ID
WHERE a.ACTIVITY_ACTION = 'STAGE_UPDATE'
  AND a.OBJECT_DATA IS NOT NULL
  AND JSON_EXTRACT(a.OBJECT_DATA, '$.stage.id') IS NOT NULL;
