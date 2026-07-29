package com.modlix.saas.adzump.model;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class CampaignPlanBody implements Serializable {

    @Serial
    private static final long serialVersionUID = 8127465091238475617L;

    private Objective objective;
    private BudgetPlan budget;
    private ScheduleConfig schedule;
    private Compliance compliance;
    private List<AdGroup> adGroups;
    private List<AssetGroup> assetGroups;
    private List<Creative> creatives;
    private LeadForm leadForm;
    private LandingPageRef landingPage;
    private JsonNode performancePolicyOverride;
    private JsonNode autonomyOverride;
    private JsonNode milestoneMappingOverride;
    private Map<String, Object> verticalExtensions;
    private Links links;
    private Map<String, Provenance> provenance;
}
