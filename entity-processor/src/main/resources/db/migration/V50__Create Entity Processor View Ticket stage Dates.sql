SET SESSION group_concat_max_len = 1000000;

SELECT CONCAT(
               'CREATE OR REPLACE VIEW entity_processor.entity_processor_view_ticket_stage_dates AS
                SELECT
                    activity.TICKET_ID AS ticket_id,
               ',
               GROUP_CONCAT(
                       CONCAT(
                               '     MAX(CAST(CASE ',
                               'WHEN LOWER(stage.NAME) = ''', distinct_stages.stage_name,
                               ''' THEN activity.ACTIVITY_DATE ',
                               'END AS DATETIME)) AS `',
                               UPPER(distinct_stages.normalized_name),
                               '_DATE`'
                       )
                       ORDER BY distinct_stages.normalized_name
                       SEPARATOR ',\n'
               ),
               '
                FROM entity_processor.entity_processor_activities activity
                JOIN entity_processor.entity_processor_stages stage
                  ON stage.ID = activity.STAGE_ID
                WHERE activity.STAGE_ID IS NOT NULL
                  AND activity.ACTIVITY_ACTION = ''STAGE_UPDATE''
                GROUP BY activity.TICKET_ID;'
       )
INTO @create_view_sql
FROM (
         SELECT
             MIN(raw_name) AS stage_name,
             normalized_name
         FROM (
                  SELECT
                      LOWER(stage.NAME) AS raw_name,
                      REPLACE(REPLACE(LOWER(stage.NAME), ' ', '_'), '-', '_') AS normalized_name
                  FROM entity_processor.entity_processor_stages stage
                           JOIN entity_processor.entity_processor_activities activity
                                ON activity.STAGE_ID = stage.ID
                  WHERE activity.ACTIVITY_ACTION = 'STAGE_UPDATE'
              ) stage_name_subquery
         GROUP BY normalized_name
     ) AS distinct_stages;

PREPARE stmt FROM @create_view_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
