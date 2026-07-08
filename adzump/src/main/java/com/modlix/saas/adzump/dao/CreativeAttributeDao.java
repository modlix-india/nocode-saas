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

    /**
     * Every attribute assignment recorded for a client, across all its creatives — the substrate J20's
     * {@code AttributeAttributor} decomposes into the account's attribute performance map. Scoped to
     * {@code clientCode}, so the map it feeds stays tenant-private (J20 §5.5).
     */
    public List<CreativeAttributeRow> findByClient(String clientCode) {

        return this.dslContext.selectFrom(ADZUMP_CREATIVE_ATTRIBUTE)
                .where(ADZUMP_CREATIVE_ATTRIBUTE.CLIENT_CODE.eq(clientCode))
                .orderBy(ADZUMP_CREATIVE_ATTRIBUTE.ID.asc())
                .fetchInto(this.pojoClass);
    }

    /**
     * Replaces a creative's attribute assignments for a client with {@code rows} — the durable write J20
     * owns (materializing a tagged creative's {@code axis -> value} map into the living attribute map).
     * Delete-then-insert within the class-level transaction keeps the creative's row set consistent (no
     * stale axes linger after a retag). Each {@code row}'s {@code clientCode}/{@code creativeId} are
     * pinned by the caller before this is called.
     */
    public void replaceForCreative(String clientCode, String creativeId, List<CreativeAttributeRow> rows) {

        this.dslContext.deleteFrom(ADZUMP_CREATIVE_ATTRIBUTE)
                .where(ADZUMP_CREATIVE_ATTRIBUTE.CLIENT_CODE.eq(clientCode))
                .and(ADZUMP_CREATIVE_ATTRIBUTE.CREATIVE_ID.eq(creativeId))
                .execute();

        if (rows == null)
            return;

        for (CreativeAttributeRow row : rows)
            this.create(row);
    }
}
