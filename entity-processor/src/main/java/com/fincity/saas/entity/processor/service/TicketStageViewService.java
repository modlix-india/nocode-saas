package com.fincity.saas.entity.processor.service;

import static com.fincity.saas.entity.processor.jooq.EntityProcessor.ENTITY_PROCESSOR;
import static com.fincity.saas.entity.processor.jooq.tables.EntityProcessorActivities.ENTITY_PROCESSOR_ACTIVITIES;
import static com.fincity.saas.entity.processor.jooq.tables.EntityProcessorStages.ENTITY_PROCESSOR_STAGES;

import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.entity.processor.enums.ActivityAction;
import com.fincity.saas.entity.processor.jooq.tables.EntityProcessorActivities;
import com.fincity.saas.entity.processor.jooq.tables.EntityProcessorStages;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Select;
import org.jooq.SelectField;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class TicketStageViewService {

    public static final String VIEW_NAME = "entity_processor_view_ticket_stage_dates";
    private static final ActivityAction STAGE_UPDATE_ACTION = ActivityAction.STAGE_UPDATE;

    public static final String TICKET_ID = "ticket_id";
    private static final String RAW_NAME = "raw_name";
    private static final String NORMALIZED_NAME = "normalized_name";
    private static final String STAGE_NAME = "stage_name";
    private static final String DATE_SUFFIX = "_date";

    private static final String ALIAS_ACTIVITY = "activity";
    private static final String ALIAS_STAGE = "stage";
    private static final String ALIAS_STAGE_NAME_SUBQUERY = "stage_name_subquery";

    private final DSLContext dsl;

    @Transactional
    public Mono<Void> rebuildTicketStageDatesView() {
        return this.fetchDistinctStages()
                .collectList()
                .flatMap(this::rebuildViewIfStagesExist)
                .doOnSuccess(
                        unused -> log.info("Successfully rebuilt view: {}.{}", ENTITY_PROCESSOR.getName(), VIEW_NAME))
                .doOnError(error ->
                        log.error("Failed to rebuild view: {}.{}", ENTITY_PROCESSOR.getName(), VIEW_NAME, error))
                .onErrorMap(error -> new GenericException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "Failed to rebuild ticket stage dates view: " + error.getMessage(),
                        error));
    }

    private Mono<Void> rebuildViewIfStagesExist(List<StageInfo> stages) {
        if (stages.isEmpty()) {
            log.warn("No stages found with activity; skipping view rebuild.");
            return Mono.empty();
        }
        return this.executeViewCreation(stages);
    }

    private Flux<StageInfo> fetchDistinctStages() {
        var stageTable = ENTITY_PROCESSOR_STAGES;
        var activityTable = ENTITY_PROCESSOR_ACTIVITIES;

        Field<String> lowerCaseStageName = DSL.lower(stageTable.NAME);
        Field<String> normalizedStageName = this.normalizeColumnName(lowerCaseStageName);

        Table<?> stageNameSubquery = DSL.select(
                        lowerCaseStageName.as(RAW_NAME), normalizedStageName.as(NORMALIZED_NAME))
                .from(stageTable)
                .join(activityTable)
                .on(activityTable.STAGE_ID.eq(stageTable.ID))
                .where(activityTable.ACTIVITY_ACTION.eq(STAGE_UPDATE_ACTION))
                .asTable(ALIAS_STAGE_NAME_SUBQUERY);

        Field<String> rawNameField = stageNameSubquery.field(RAW_NAME, String.class);
        Field<String> normalizedNameField = stageNameSubquery.field(NORMALIZED_NAME, String.class);

        var distinctStagesQuery = dsl.select(DSL.min(rawNameField).as(STAGE_NAME), normalizedNameField)
                .from(stageNameSubquery)
                .groupBy(normalizedNameField)
                .orderBy(normalizedNameField);

        return Flux.from(distinctStagesQuery).map(this::mapToStageInfo);
    }

    private Mono<Void> executeViewCreation(List<StageInfo> stages) {
        Select<?> viewQuery = this.buildViewQuery(stages);
        Table<Record> viewTable = DSL.table(DSL.name(ENTITY_PROCESSOR.getName(), VIEW_NAME));

        return Mono.from(dsl.createOrReplaceView(viewTable).as(viewQuery))
                .doOnSuccess(result -> log.info("View rebuilt successfully with {} stage columns", stages.size()))
                .then();
    }

    private Select<? extends Record> buildViewQuery(List<StageInfo> stages) {
        EntityProcessorActivities activityTable = ENTITY_PROCESSOR_ACTIVITIES.as(ALIAS_ACTIVITY);
        EntityProcessorStages stageTable = ENTITY_PROCESSOR_STAGES.as(ALIAS_STAGE);

        List<SelectField<?>> selectColumns = this.buildSelectColumns(stages, activityTable, stageTable);

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

        columns.add(activityTable.TICKET_ID.as(TICKET_ID));

        stages.stream()
                .map(stageInfo -> this.buildStageDateColumn(stageInfo, activityTable, stageTable))
                .forEach(columns::add);

        return columns;
    }

    private Field<?> buildStageDateColumn(
            StageInfo stageInfo, EntityProcessorActivities activityTable, EntityProcessorStages stageTable) {

        String columnName = this.sanitizeColumnName(stageInfo.normalizedName()) + DATE_SUFFIX;

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
        return new StageInfo(rec.get(STAGE_NAME, String.class), rec.get(NORMALIZED_NAME, String.class));
    }

    public record StageInfo(String stageName, String normalizedName) {}
}
