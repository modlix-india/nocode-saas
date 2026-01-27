package com.fincity.saas.entity.processor.service;

import static com.fincity.saas.entity.processor.jooq.tables.EntityProcessorActivities.ENTITY_PROCESSOR_ACTIVITIES;
import static com.fincity.saas.entity.processor.jooq.tables.EntityProcessorStages.ENTITY_PROCESSOR_STAGES;

import java.util.ArrayList;
import java.util.List;

import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Select;
import org.jooq.SelectField;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fincity.saas.entity.processor.enums.ActivityAction;
import com.fincity.saas.entity.processor.jooq.tables.EntityProcessorActivities;
import com.fincity.saas.entity.processor.jooq.tables.EntityProcessorStages;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class TicketStageViewService {

    private static final String VIEW_NAME = "entity_processor_view_ticket_stage_dates";
    private static final String SCHEMA_NAME = "entity_processor";
    private static final ActivityAction STAGE_UPDATE_ACTION = ActivityAction.STAGE_UPDATE;

    private static final String COL_TICKET_ID = "ticket_id";
    private static final String COL_RAW_NAME = "raw_name";
    private static final String COL_NORMALIZED_NAME = "normalized_name";
    private static final String COL_STAGE_NAME = "stage_name";
    private static final String DATE_SUFFIX = "_date";

    private static final String ALIAS_ACTIVITY = "activity";
    private static final String ALIAS_STAGE = "stage";
    private static final String ALIAS_STAGE_NAME_SUBQUERY = "stage_name_subquery";

    private final DSLContext dsl;

    @Transactional
    public Mono<Void> rebuildTicketStageDatesView() {
        return fetchDistinctStages()
                .collectList()
                .flatMap(this::rebuildViewIfStagesExist)
                .doOnSuccess(unused -> log.info("Successfully rebuilt view: {}.{}", SCHEMA_NAME, VIEW_NAME))
                .doOnError(error -> log.error("Failed to rebuild view: {}.{}", SCHEMA_NAME, VIEW_NAME, error))
                .onErrorMap(error -> new ViewRebuildException("Failed to rebuild ticket stage dates view", error));
    }

    private Mono<Void> rebuildViewIfStagesExist(List<StageInfo> stages) {
        if (stages.isEmpty()) {
            log.warn("No stages found with activity; skipping view rebuild.");
            return Mono.empty();
        }
        return executeViewCreation(stages);
    }

    private Flux<StageInfo> fetchDistinctStages() {
        var stageTable = ENTITY_PROCESSOR_STAGES;
        var activityTable = ENTITY_PROCESSOR_ACTIVITIES;

        Field<String> lowerCaseStageName = DSL.lower(stageTable.NAME);
        Field<String> normalizedStageName = normalizeColumnName(lowerCaseStageName);

        Table<?> stageNameSubquery = DSL.select(
                        lowerCaseStageName.as(COL_RAW_NAME), normalizedStageName.as(COL_NORMALIZED_NAME))
                .from(stageTable)
                .join(activityTable)
                .on(activityTable.STAGE_ID.eq(stageTable.ID))
                .where(activityTable.ACTIVITY_ACTION.eq(STAGE_UPDATE_ACTION))
                .asTable(ALIAS_STAGE_NAME_SUBQUERY);

        Field<String> rawNameField = stageNameSubquery.field(COL_RAW_NAME, String.class);
        Field<String> normalizedNameField = stageNameSubquery.field(COL_NORMALIZED_NAME, String.class);

        var distinctStagesQuery = dsl.select(DSL.min(rawNameField).as(COL_STAGE_NAME), normalizedNameField)
                .from(stageNameSubquery)
                .groupBy(normalizedNameField)
                .orderBy(normalizedNameField);

        return Flux.from(distinctStagesQuery).map(this::mapToStageInfo);
    }

    private Mono<Void> executeViewCreation(List<StageInfo> stages) {
        var viewQuery = buildViewQuery(stages);
        var viewTable = DSL.table(DSL.name(SCHEMA_NAME, VIEW_NAME));

        return Mono.from(dsl.createOrReplaceView(viewTable).as(viewQuery))
                .doOnSuccess(result -> log.info("View rebuilt successfully with {} stage columns", stages.size()))
                .then();
    }

    private Select<? extends Record> buildViewQuery(List<StageInfo> stages) {
        var activityTable = ENTITY_PROCESSOR_ACTIVITIES.as(ALIAS_ACTIVITY);
        var stageTable = ENTITY_PROCESSOR_STAGES.as(ALIAS_STAGE);

        List<SelectField<?>> selectColumns = buildSelectColumns(stages, activityTable, stageTable);

        return DSL.select(selectColumns)
                .from(activityTable)
                .join(stageTable)
                .on(stageTable.ID.eq(activityTable.STAGE_ID))
                .where(activityTable.STAGE_ID.isNotNull())
                .and(activityTable.ACTIVITY_ACTION.eq(STAGE_UPDATE_ACTION))
                .groupBy(activityTable.TICKET_ID);
    }

    private List<SelectField<?>> buildSelectColumns(
            List<StageInfo> stages, EntityProcessorActivities activityTable, EntityProcessorStages stageTable) {

        List<SelectField<?>> columns = new ArrayList<>(stages.size() + 1);

        columns.add(activityTable.TICKET_ID.as(COL_TICKET_ID));

        stages.stream()
                .map(stageInfo -> this.buildStageDateColumn(stageInfo, activityTable, stageTable))
                .forEach(columns::add);

        return columns;
    }

    private Field<?> buildStageDateColumn(
            StageInfo stageInfo, EntityProcessorActivities activityTable, EntityProcessorStages stageTable) {

        String columnName = sanitizeColumnName(stageInfo.normalizedName()) + DATE_SUFFIX;

        Field<?> caseWhenExpression =
                DSL.when(DSL.lower(stageTable.NAME).eq(stageInfo.stageName()), activityTable.ACTIVITY_DATE);

        return DSL.max(DSL.cast(caseWhenExpression, SQLDataType.LOCALDATETIME)).as(DSL.name(columnName));
    }

    private Field<String> normalizeColumnName(Field<String> input) {
        return DSL.replace(DSL.replace(input, " ", "_"), "-", "_");
    }

    private String sanitizeColumnName(String name) {
        if (name == null) return "";

        return name.replaceAll("[^a-z0-9_]", "");
    }

    private StageInfo mapToStageInfo(Record rec) {
        return new StageInfo(rec.get(COL_STAGE_NAME, String.class), rec.get(COL_NORMALIZED_NAME, String.class));
    }

    public record StageInfo(String stageName, String normalizedName) {}

    public static class ViewRebuildException extends RuntimeException {
        public ViewRebuildException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
