SET SESSION group_concat_max_len = 1000000;

SELECT CONCAT(
               'CREATE OR REPLACE VIEW entity_processor.entity_processor_view_ticket_stage_dates AS
                SELECT
                    a.TICKET_ID AS TICKET_ID,
               ',
               GROUP_CONCAT(
                       CONCAT(
                               '     MAX(CAST(CASE ',
                               'WHEN LOWER(s.NAME) = ''', x.stage_name,
                               ''' THEN a.ACTIVITY_DATE ',
                               'END AS DATETIME)) AS `',
                               UPPER(x.normalized_name),
                               '_DATE`'
                       )
                       ORDER BY x.normalized_name
                       SEPARATOR ',\n'
               ),
               '
                FROM entity_processor.entity_processor_activities a
                JOIN entity_processor.entity_processor_stages s
                  ON s.ID = a.STAGE_ID
                WHERE a.STAGE_ID IS NOT NULL
                  AND a.ACTIVITY_ACTION = ''STAGE_UPDATE''
                GROUP BY a.TICKET_ID;'
       )
INTO @create_view_sql
FROM (
         SELECT
             MIN(raw_name) AS stage_name,
             normalized_name
         FROM (
                  SELECT
                      LOWER(s.NAME) AS raw_name,
                      REPLACE(REPLACE(LOWER(s.NAME), ' ', '_'), '-', '_') AS normalized_name
                  FROM entity_processor.entity_processor_stages s
                           JOIN entity_processor.entity_processor_activities a
                                ON a.STAGE_ID = s.ID
              ) t
         GROUP BY normalized_name
     ) AS x;

PREPARE stmt FROM @create_view_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
