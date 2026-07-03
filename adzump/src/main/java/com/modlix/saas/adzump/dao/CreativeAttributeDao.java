package com.modlix.saas.adzump.dao;

import static com.modlix.saas.adzump.jooq.tables.AdzumpCreativeAttribute.ADZUMP_CREATIVE_ATTRIBUTE;

import java.util.List;

import org.jooq.types.ULong;
import org.springframework.stereotype.Service;

import com.modlix.saas.adzump.dto.CreativeAttributeRow;
import com.modlix.saas.adzump.jooq.tables.records.AdzumpCreativeAttributeRecord;
import com.modlix.saas.commons2.jooq.dao.AbstractDAO;

/**
 * The creative-attribute table carries no JSON columns (and no audit columns),
 * so the plain commons2 {@link AbstractDAO} mapping works as-is.
 */
@Service
public class CreativeAttributeDao extends AbstractDAO<AdzumpCreativeAttributeRecord, ULong, CreativeAttributeRow> {

    public CreativeAttributeDao() {
        super(CreativeAttributeRow.class, ADZUMP_CREATIVE_ATTRIBUTE, ADZUMP_CREATIVE_ATTRIBUTE.ID);
    }

    /**
     * Returns all attribute rows (axis/value pairs) recorded for a creative,
     * across every axis. The result is empty when the creative has none.
     */
    public List<CreativeAttributeRow> findByCreativeId(String creativeId) {

        return this.dslContext.selectFrom(ADZUMP_CREATIVE_ATTRIBUTE)
                .where(ADZUMP_CREATIVE_ATTRIBUTE.CREATIVE_ID.eq(creativeId))
                .orderBy(ADZUMP_CREATIVE_ATTRIBUTE.ID.asc())
                .fetchInto(this.pojoClass);
    }
}
