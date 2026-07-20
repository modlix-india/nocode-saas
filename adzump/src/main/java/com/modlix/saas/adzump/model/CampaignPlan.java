package com.modlix.saas.adzump.model;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.jooq.types.ULong;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.modlix.saas.adzump.enums.CampaignType;
import com.modlix.saas.adzump.enums.Platform;
import com.modlix.saas.adzump.jooq.enums.AdzumpCampaignPlanStatus;
import com.modlix.saas.commons2.model.dto.AbstractUpdatableDTO;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.Accessors;

@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@ToString(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true) // tolerate the wire "platforms" array (derived here, never stored)
public class CampaignPlan extends AbstractUpdatableDTO<ULong, ULong> {

    @Serial
    private static final long serialVersionUID = -6482913740125836917L;

    private String schemaVersion;
    private Integer revision;
    private String clientCode;
    private AdzumpCampaignPlanStatus status;
    private String name;
    private String productId;
    private String productTemplateId;
    private String vertical;
    private Map<Platform, CampaignType> campaignTypes;
    private CampaignPlanBody body;

    /**
     * Platform participation is DERIVED from the {@link #campaignTypes} keys; there is no stored
     * {@code platforms} column. The DAO decomposes {@code campaignTypes} onto the
     * {@code google_campaign_type} / {@code meta_campaign_type} enum columns. Serialized out for the
     * wire contract; any incoming {@code platforms} value on the wire is ignored (read-only).
     */
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    public List<Platform> getPlatforms() {
        if (this.campaignTypes == null || this.campaignTypes.isEmpty())
            return List.of();
        return new ArrayList<>(this.campaignTypes.keySet());
    }
}
