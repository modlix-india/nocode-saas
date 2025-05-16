package com.fincity.saas.entity.collector.dao;

import com.fincity.saas.commons.jooq.dao.AbstractUpdatableDAO;
import com.fincity.saas.commons.model.dto.AbstractUpdatableDTO;
import com.fincity.saas.entity.collector.dto.EntityCollectorLog;
import com.fincity.saas.entity.collector.jooq.tables.records.EntityCollectorLogRecord;
import org.jooq.UpdatableRecord;
import org.jooq.types.ULong;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import static com.fincity.saas.entity.collector.jooq.tables.EntityCollectorLog.ENTITY_COLLECTOR_LOG;

@Repository
public class EntityCollectorLogDAO extends AbstractUpdatableDAO<EntityCollectorLogRecord, ULong, EntityCollectorLog> {

    protected EntityCollectorLogDAO() {
        super(EntityCollectorLog.class, ENTITY_COLLECTOR_LOG, ENTITY_COLLECTOR_LOG.ID);
    }

    @Override
    public <A extends AbstractUpdatableDTO<ULong, ULong>> Mono<EntityCollectorLog> update(A entity) {

        entity.setUpdatedAt(null);
        UpdatableRecord<EntityCollectorLogRecord> rec = this.dslContext.newRecord(this.table);
        rec.from(entity);
        rec.reset("CREATED_AT");
        return Mono.from(this.dslContext.update(this.table)
                        .set(rec)
                        .where(this.idField.eq(entity.getId())))
                .thenReturn(rec.into(this.pojoClass));
    }
}