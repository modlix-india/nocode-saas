package com.modlix.saas.adzump.dao;

import static com.modlix.saas.adzump.jooq.tables.AdzumpCampaignPlan.ADZUMP_CAMPAIGN_PLAN;

import java.util.EnumMap;
import java.util.Map;

import org.jooq.JSON;
import org.jooq.Record;
import org.jooq.types.ULong;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.modlix.saas.adzump.enums.CampaignType;
import com.modlix.saas.adzump.enums.Platform;
import com.modlix.saas.adzump.jooq.enums.AdzumpCampaignPlanGoogleCampaignType;
import com.modlix.saas.adzump.jooq.enums.AdzumpCampaignPlanMetaCampaignType;
import com.modlix.saas.adzump.jooq.tables.records.AdzumpCampaignPlanRecord;
import com.modlix.saas.adzump.model.CampaignPlan;
import com.modlix.saas.adzump.model.CampaignPlanBody;
import com.modlix.saas.adzump.util.JsonMergePatchUtil;
import com.modlix.saas.commons2.exception.GenericException;

@Service
public class CampaignPlanDao extends AbstractAdzumpJsonDAO<AdzumpCampaignPlanRecord, CampaignPlan> {

    public CampaignPlanDao() {
        super(CampaignPlan.class, ADZUMP_CAMPAIGN_PLAN, ADZUMP_CAMPAIGN_PLAN.ID);
    }

    /**
     * Recomposes the {@code campaignTypes} map from the two per-platform enum
     * columns (a non-null column means the plan targets that platform) and reads
     * the nested plan {@code body}. {@code platforms} is derived by the model
     * from the map's keys, so it is not set here.
     */
    @Override
    protected void readCustomColumns(Record rec, CampaignPlan plan) {

        AdzumpCampaignPlanGoogleCampaignType google = rec.get(ADZUMP_CAMPAIGN_PLAN.GOOGLE_CAMPAIGN_TYPE);
        AdzumpCampaignPlanMetaCampaignType meta = rec.get(ADZUMP_CAMPAIGN_PLAN.META_CAMPAIGN_TYPE);

        Map<Platform, CampaignType> campaignTypes = new EnumMap<>(Platform.class);
        if (google != null)
            campaignTypes.put(Platform.GOOGLE, CampaignType.valueOf(google.name()));
        if (meta != null)
            campaignTypes.put(Platform.META, CampaignType.valueOf(meta.name()));
        plan.setCampaignTypes(campaignTypes);

        plan.setBody(fromJson(getJson(rec, ADZUMP_CAMPAIGN_PLAN.BODY), CampaignPlanBody.class));
    }

    /**
     * Decomposes the {@code campaignTypes} map onto the two per-platform enum
     * columns and serializes the plan {@code body}. Both enum columns are always
     * written (null when the platform is absent) so a full-replace update clears
     * a platform that was dropped from the map.
     */
    @Override
    protected void writeCustomColumns(CampaignPlan plan, AdzumpCampaignPlanRecord rec) {

        Map<Platform, CampaignType> campaignTypes = plan.getCampaignTypes();

        AdzumpCampaignPlanGoogleCampaignType google = null;
        AdzumpCampaignPlanMetaCampaignType meta = null;

        if (campaignTypes != null) {
            CampaignType g = campaignTypes.get(Platform.GOOGLE);
            if (g != null)
                google = AdzumpCampaignPlanGoogleCampaignType.valueOf(g.name());
            CampaignType m = campaignTypes.get(Platform.META);
            if (m != null)
                meta = AdzumpCampaignPlanMetaCampaignType.valueOf(m.name());
        }

        rec.set(ADZUMP_CAMPAIGN_PLAN.GOOGLE_CAMPAIGN_TYPE, google);
        rec.set(ADZUMP_CAMPAIGN_PLAN.META_CAMPAIGN_TYPE, meta);
        rec.set(ADZUMP_CAMPAIGN_PLAN.BODY, toJson(plan.getBody()));
    }

    /**
     * Applies an RFC 7386 merge patch over the plan's JSON {@code body} and bumps
     * {@code revision} with an optimistic concurrency check: the UPDATE only
     * applies while the row still carries the revision that was read; zero rows
     * updated means a concurrent writer won and a {@code 409 CONFLICT} is thrown.
     */
    public CampaignPlan applyMergePatch(ULong id, JsonNode patch) {

        Record rec = this.getRecordById(id);

        Integer revision = rec.get(ADZUMP_CAMPAIGN_PLAN.REVISION);
        JSON bodyJson = rec.get(ADZUMP_CAMPAIGN_PLAN.BODY);

        JsonNode body;
        try {
            body = bodyJson == null || bodyJson.data() == null
                    ? MAPPER.createObjectNode()
                    : MAPPER.readTree(bodyJson.data());
        } catch (JsonProcessingException e) {
            throw new GenericException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Unable to parse the body of the campaign plan : " + id, e);
        }

        JsonNode merged = JsonMergePatchUtil.merge(body, patch);

        int rows = this.dslContext.update(ADZUMP_CAMPAIGN_PLAN)
                .set(ADZUMP_CAMPAIGN_PLAN.BODY, toJson(merged))
                .set(ADZUMP_CAMPAIGN_PLAN.REVISION, ADZUMP_CAMPAIGN_PLAN.REVISION.plus(1))
                .where(ADZUMP_CAMPAIGN_PLAN.ID.eq(id)
                        .and(ADZUMP_CAMPAIGN_PLAN.REVISION.eq(revision)))
                .execute();

        if (rows == 0)
            throw new GenericException(HttpStatus.CONFLICT,
                    "Campaign plan " + id + " was modified concurrently (stale revision : " + revision
                            + "), please re-read and retry");

        return this.readById(id);
    }
}
