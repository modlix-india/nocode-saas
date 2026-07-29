package com.modlix.saas.adzump.dao;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.JSON;
import org.jooq.Record;
import org.jooq.Record1;
import org.jooq.SelectJoinStep;
import org.jooq.SortField;
import org.jooq.SortOrder;
import org.jooq.Table;
import org.jooq.UpdatableRecord;
import org.jooq.types.ULong;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.modlix.saas.commons2.exception.GenericException;
import com.modlix.saas.commons2.jooq.dao.AbstractUpdatableDAO;
import com.modlix.saas.commons2.model.condition.AbstractCondition;
import com.modlix.saas.commons2.model.dto.AbstractUpdatableDTO;
import com.modlix.saas.commons2.util.Tuples.Tuple2;

/**
 * Base DAO for adzump tables that carry columns the default JOOQ record &lt;-&gt;
 * POJO field mapper cannot translate: {@code JSON} columns (mapped to
 * {@link com.fasterxml.jackson.databind.JsonNode} / typed bodies) and, for the
 * plan table, the {@code google_campaign_type} / {@code meta_campaign_type} enum
 * columns that are recomposed into the wire-level {@code campaignTypes} map.
 *
 * <p>The commons2 {@code AbstractDAO} performs its mapping inline through
 * {@code Record.into(pojoClass)} (record -&gt; pojo) and {@code Record.from(pojo)}
 * (pojo -&gt; record) inside {@code create}/{@code readById}/{@code readAll}/
 * {@code list}/{@code update}; there is no per-field mapping hook to plug into.
 * This base therefore overrides those methods and funnels every conversion
 * through {@link #toPojo(Record)} / {@link #toRecord(AbstractUpdatableDTO)},
 * which map the plain columns via JOOQ ({@code Record.into(Field...)} /
 * {@code Record.from(Object, Field...)}) and delegate the remaining columns to
 * the {@link #readCustomColumns} / {@link #writeCustomColumns} subclass hooks,
 * all through a single shared {@link ObjectMapper}.
 */
@Transactional
public abstract class AbstractAdzumpJsonDAO<R extends UpdatableRecord<R>, D extends AbstractUpdatableDTO<ULong, ULong>>
        extends AbstractUpdatableDAO<R, ULong, D> {

    protected static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    /** Every column except the JSON ones; these are mapped by JOOQ directly. */
    protected final Field<?>[] plainFields;

    protected AbstractAdzumpJsonDAO(Class<D> pojoClass, Table<R> table, Field<ULong> idField) {

        super(pojoClass, table, idField);

        this.plainFields = Arrays.stream(table.fields())
                .filter(f -> !f.getDataType().isJSON())
                .toArray(Field[]::new);
    }

    /**
     * Sets the pojo fields that the plain JOOQ mapping cannot (JSON bodies and,
     * for the plan, the decomposed campaign-type enum columns) from {@code rec},
     * which is always a full row. Use {@link #getJson(Record, Field)} /
     * {@link #fromJson}.
     */
    protected abstract void readCustomColumns(Record rec, D pojo);

    /**
     * Sets the record columns that the plain JOOQ mapping cannot from
     * {@code pojo}. Always set every such column explicitly (even to {@code null})
     * so full-replace updates clear stale values. Use {@link #toJson(Object)}.
     */
    protected abstract void writeCustomColumns(D pojo, R rec);

    protected D toPojo(Record rec) {

        if (rec == null)
            return null;

        D pojo = rec.into(this.plainFields).into(this.pojoClass);
        this.readCustomColumns(rec, pojo);
        return pojo;
    }

    protected R toRecord(D pojo) {

        R rec = this.dslContext.newRecord(this.table);
        rec.from(pojo, this.plainFields);
        this.writeCustomColumns(pojo, rec);
        return rec;
    }

    @Override
    public D create(D pojo) {

        pojo.setId(null);

        R rec = this.toRecord(pojo);

        ULong id = this.dslContext
                .insertInto(this.table)
                .set(rec)
                .returning(this.idField)
                .fetchOne()
                .get(0, this.idField.getType());

        return this.readById(id);
    }

    @Override
    public D readById(ULong id) {
        return this.toPojo(this.getRecordById(id));
    }

    @Override
    public List<D> readAll(AbstractCondition query) {

        Tuple2<SelectJoinStep<Record>, SelectJoinStep<Record1<Integer>>> selectJoinStepTuple = this
                .getSelectJointStep();
        Condition condition = this.filter(query, selectJoinStepTuple.getT1());
        return this.dslContext.fetch(selectJoinStepTuple.getT1().where(condition)).map(this::toPojo);
    }

    @Override
    protected Page<D> list(Pageable pageable,
            Tuple2<SelectJoinStep<Record>, SelectJoinStep<Record1<Integer>>> selectJoinStepTuple) {

        List<SortField<?>> orderBy = new ArrayList<>();

        pageable.getSort().forEach(order -> {
            Field<?> field = this.getField(order.getProperty(), selectJoinStepTuple.getT1());
            if (field != null)
                orderBy.add(field.sort(order.getDirection() == Direction.ASC ? SortOrder.ASC : SortOrder.DESC));
        });

        final Integer recordsCount = this.dslContext.fetchOne(selectJoinStepTuple.getT2()).value1();

        SelectJoinStep<Record> selectJoinStep = selectJoinStepTuple.getT1();
        if (!orderBy.isEmpty())
            selectJoinStep.orderBy(orderBy);

        List<D> recordsList = this.dslContext
                .fetch(selectJoinStep.limit(pageable.getPageSize()).offset(pageable.getOffset()))
                .map(this::toPojo);

        return PageableExecutionUtils.getPage(recordsList, pageable, () -> recordsCount);
    }

    @Override
    public <A extends AbstractUpdatableDTO<ULong, ULong>> D update(A entity) {

        entity.setUpdatedAt(null);

        R rec = this.toRecord(this.pojoClass.cast(entity));
        rec.reset("CREATED_BY");
        rec.reset("CREATED_AT");

        this.dslContext.update(this.table)
                .set(rec)
                .where(this.idField.eq(entity.getId()))
                .execute();

        return this.readById(entity.getId());
    }

    @Override
    public D update(ULong id, Map<String, Object> updateFields) {

        updateFields.remove("createdAt");

        Map<Field<?>, Object> fields = new HashMap<>();
        for (Map.Entry<String, Object> entry : updateFields.entrySet()) {

            Field<?> field = this.getField(entry.getKey());
            if (field == null)
                continue;

            Object value = entry.getValue();
            if (value != null && field.getDataType().isJSON())
                value = toJson(value);

            fields.put(field, value);
        }

        this.dslContext.update(this.table)
                .set(fields)
                .where(this.idField.eq(id))
                .execute();

        return this.readById(id);
    }

    protected static JSON getJson(Record rec, Field<JSON> field) {
        return rec.field(field) == null ? null : rec.get(field);
    }

    protected static JSON toJson(Object value) {

        if (value == null)
            return null;

        if (value instanceof JSON json)
            return json;

        if (value instanceof String str)
            return JSON.valueOf(str);

        try {
            return JSON.valueOf(MAPPER.writeValueAsString(value));
        } catch (JsonProcessingException e) {
            throw new GenericException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Unable to serialize the value to a JSON column : " + value, e);
        }
    }

    protected static <T> T fromJson(JSON json, Class<T> type) {

        if (json == null || json.data() == null)
            return null;

        try {
            return MAPPER.readValue(json.data(), type);
        } catch (JsonProcessingException e) {
            throw new GenericException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Unable to parse the JSON column value : " + json.data(), e);
        }
    }

    protected static <T> T fromJson(JSON json, TypeReference<T> type) {

        if (json == null || json.data() == null)
            return null;

        try {
            return MAPPER.readValue(json.data(), type);
        } catch (JsonProcessingException e) {
            throw new GenericException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Unable to parse the JSON column value : " + json.data(), e);
        }
    }
}
