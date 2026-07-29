package com.modlix.saas.adzump.platform.google;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.ads.googleads.v24.common.AdTextAsset;
import com.google.ads.googleads.v24.common.KeywordInfo;
import com.google.ads.googleads.v24.common.MaximizeConversionValue;
import com.google.ads.googleads.v24.common.MaximizeConversions;
import com.google.ads.googleads.v24.common.ResponsiveSearchAdInfo;
import com.google.ads.googleads.v24.common.TargetSpend;
import com.google.ads.googleads.v24.enums.AdGroupAdStatusEnum.AdGroupAdStatus;
import com.google.ads.googleads.v24.enums.AdGroupCriterionStatusEnum.AdGroupCriterionStatus;
import com.google.ads.googleads.v24.enums.AdGroupStatusEnum.AdGroupStatus;
import com.google.ads.googleads.v24.enums.AdGroupTypeEnum.AdGroupType;
import com.google.ads.googleads.v24.enums.AdvertisingChannelTypeEnum.AdvertisingChannelType;
import com.google.ads.googleads.v24.enums.BudgetDeliveryMethodEnum.BudgetDeliveryMethod;
import com.google.ads.googleads.v24.enums.CampaignStatusEnum.CampaignStatus;
import com.google.ads.googleads.v24.enums.ConversionActionStatusEnum.ConversionActionStatus;
import com.google.ads.googleads.v24.enums.ConversionActionTypeEnum.ConversionActionType;
import com.google.ads.googleads.v24.enums.KeywordMatchTypeEnum.KeywordMatchType;
import com.google.ads.googleads.v24.resources.Ad;
import com.google.ads.googleads.v24.resources.AdGroup;
import com.google.ads.googleads.v24.resources.AdGroupAd;
import com.google.ads.googleads.v24.resources.AdGroupCriterion;
import com.google.ads.googleads.v24.resources.Campaign;
import com.google.ads.googleads.v24.resources.CampaignBudget;
import com.google.ads.googleads.v24.resources.ConversionAction;
import com.google.ads.googleads.v24.services.AdGroupAdOperation;
import com.google.ads.googleads.v24.services.AdGroupCriterionOperation;
import com.google.ads.googleads.v24.services.AdGroupOperation;
import com.google.ads.googleads.v24.services.CampaignBudgetOperation;
import com.google.ads.googleads.v24.services.CampaignOperation;
import com.google.ads.googleads.v24.services.ConversionActionOperation;
import com.google.ads.googleads.v24.services.MutateGoogleAdsResponse;
import com.google.ads.googleads.v24.services.MutateOperation;
import com.google.ads.googleads.v24.services.MutateOperationResponse;
import com.google.ads.googleads.v24.utils.ResourceNames;
import com.google.protobuf.FieldMask;
import com.modlix.saas.adzump.enums.Platform;
import com.modlix.saas.adzump.model.Links;
import com.modlix.saas.adzump.model.Money;
import com.modlix.saas.adzump.platform.BidSpec;
import com.modlix.saas.adzump.platform.CompiledCampaign;
import com.modlix.saas.adzump.platform.CompiledCreative;
import com.modlix.saas.adzump.platform.CreativeRef;
import com.modlix.saas.adzump.platform.LaunchResult;
import com.modlix.saas.adzump.platform.PlatformRef;
import com.modlix.saas.adzump.platform.RunState;
import com.modlix.saas.adzump.platform.TargetingPatch;
import com.modlix.saas.adzump.platform.Token;
import com.modlix.saas.adzump.service.AdzumpMessageResourceService;
import com.modlix.saas.commons2.exception.GenericException;

/**
 * The Google lifecycle (J4 §5.3–5.4): translates the compiled {@link CompiledCampaign} payload tree
 * (built by the J7 {@code GoogleSearchCompiler}) into Google Ads v24 {@code MutateOperation}s and the
 * SPI mutations into the matching campaign-budget / bidding-strategy / criterion / ad-group-ad
 * mutates. {@link #launchPaused} builds the whole campaign→ad-group(s)→RSA + keywords + negatives
 * tree — everything PAUSED — as a <b>single atomic</b> {@code GoogleAdsService.mutate} keyed by
 * negative temp resource names, so a failure leaves nothing rather than a half-built ACTIVE campaign.
 *
 * <p>All proto building here is pure and offline-testable: {@link GoogleAdsClientFacade} is the only
 * I/O, and the tests mock it and assert on the captured operations.
 */
@Component
public class GoogleLifecycle {

    /** Negative temp ids: "create me in this mutate, others may reference this resource name". */
    private static final long BUDGET_TEMP = -1L;
    private static final long CAMPAIGN_TEMP = -2L;
    private static final long AD_GROUP_TEMP_BASE = -100L;

    private static final BigDecimal MICROS = BigDecimal.valueOf(1_000_000L);
    private static final String STATUS_FIELD = "status";

    private final GoogleAdsClientFacade facade;
    private final AdzumpMessageResourceService msg;

    public GoogleLifecycle(GoogleAdsClientFacade facade, AdzumpMessageResourceService msg) {
        this.facade = facade;
        this.msg = msg;
    }

    // --- launch ---------------------------------------------------------------------------------

    public LaunchResult launchPaused(CompiledCampaign compiled, Token token) {

        long customerId = GoogleTokens.requireCustomerId(token, this.msg);
        GoogleTokens.requireLoginCustomerId(token, this.msg); // MCC required for every Google call
        String customerIdStr = Long.toString(customerId);

        JsonNode payload = compiled == null ? null : compiled.payload();
        JsonNode campaign = payload == null ? null : payload.get("campaign");
        if (campaign == null || campaign.isNull())
            this.msg.throwMessage(m -> new GenericException(HttpStatus.BAD_REQUEST, m),
                    AdzumpMessageResourceService.FIELDS_MISSING, "compiled Google campaign payload");

        List<MutateOperation> ops = buildLaunchOperations(customerId, payload, campaign);

        MutateGoogleAdsResponse response = this.facade.mutate(token, customerIdStr, ops);

        String campaignId = extractCampaignId(response);
        Links links = new Links().setGoogle(new Links.GoogleLinks()
                .setAdAccountId(customerIdStr)
                .setCampaignId(campaignId));
        return LaunchResult.ok(Platform.GOOGLE, links);
    }

    private List<MutateOperation> buildLaunchOperations(long customerId, JsonNode payload, JsonNode campaign) {

        List<MutateOperation> ops = new ArrayList<>();

        String budgetRes = ResourceNames.campaignBudget(customerId, BUDGET_TEMP);
        String campaignRes = ResourceNames.campaign(customerId, CAMPAIGN_TEMP);

        ops.add(op(CampaignBudgetOperation.newBuilder().setCreate(buildBudget(campaign, budgetRes)).build()));
        ops.add(op(CampaignOperation.newBuilder().setCreate(buildCampaign(campaign, campaignRes, budgetRes)).build()));

        long agTemp = AD_GROUP_TEMP_BASE;
        for (JsonNode adGroup : arr(payload, "adGroups")) {
            String adGroupRes = ResourceNames.adGroup(customerId, agTemp--);
            ops.add(op(AdGroupOperation.newBuilder().setCreate(buildAdGroup(adGroup, adGroupRes, campaignRes)).build()));

            for (JsonNode keyword : arr(adGroup, "keywords"))
                ops.add(op(AdGroupCriterionOperation.newBuilder()
                        .setCreate(buildKeywordCriterion(adGroupRes, keyword, false)).build()));
            for (JsonNode negative : arr(adGroup, "negativeKeywords"))
                ops.add(op(AdGroupCriterionOperation.newBuilder()
                        .setCreate(buildKeywordCriterion(adGroupRes, negative, true)).build()));

            for (JsonNode ad : arr(adGroup, "ads"))
                ops.add(op(AdGroupAdOperation.newBuilder().setCreate(buildAdGroupAd(adGroupRes, ad)).build()));
        }

        // Conversion actions (J4 §5.3). The SEARCH compiler does not emit these yet, so this branch is
        // inert today; when a "conversionActions" node is present it is created in the SAME atomic mutate
        // so tracking is wired transactionally with the campaign.
        for (JsonNode conversion : arr(payload, "conversionActions"))
            ops.add(op(ConversionActionOperation.newBuilder()
                    .setCreate(buildConversionAction(conversion)).build()));

        return ops;
    }

    // --- builders (payload tree -> protos) ------------------------------------------------------

    private CampaignBudget buildBudget(JsonNode campaign, String budgetRes) {
        CampaignBudget.Builder budget = CampaignBudget.newBuilder()
                .setResourceName(budgetRes)
                .setName(text(campaign, "name", "Campaign") + " Budget")
                .setDeliveryMethod(BudgetDeliveryMethod.STANDARD)
                .setExplicitlyShared(false);
        JsonNode node = campaign.get("campaignBudget");
        if (node != null && node.hasNonNull("amountMicros"))
            budget.setAmountMicros(node.get("amountMicros").asLong());
        return budget.build();
    }

    private Campaign buildCampaign(JsonNode campaign, String campaignRes, String budgetRes) {
        Campaign.Builder builder = Campaign.newBuilder()
                .setResourceName(campaignRes)
                .setName(text(campaign, "name", ""))
                .setStatus(CampaignStatus.PAUSED)
                .setAdvertisingChannelType(channelType(text(campaign, "advertisingChannelType", "SEARCH")))
                .setCampaignBudget(budgetRes);

        applyBidding(builder, campaign);

        JsonNode network = campaign.get("networkSettings");
        if (network != null && !network.isNull())
            builder.setNetworkSettings(Campaign.NetworkSettings.newBuilder()
                    .setTargetGoogleSearch(bool(network, "targetGoogleSearch"))
                    .setTargetSearchNetwork(bool(network, "targetSearchNetwork"))
                    .setTargetContentNetwork(bool(network, "targetContentNetwork"))
                    .setTargetPartnerSearchNetwork(bool(network, "targetPartnerSearchNetwork")));

        // v24 renamed campaign start_date/end_date to start_date_time/end_date_time (which take
        // "yyyy-MM-dd HH:mm:ss" in the account timezone). The J7 compiler emits a date-only value, so
        // anchor it to the day boundary here rather than change the compiler's golden output.
        if (campaign.hasNonNull("startDate"))
            builder.setStartDateTime(toDateTime(campaign.get("startDate").asText(), "00:00:00"));
        if (campaign.hasNonNull("endDate"))
            builder.setEndDateTime(toDateTime(campaign.get("endDate").asText(), "23:59:59"));

        return builder.build();
    }

    private void applyBidding(Campaign.Builder builder, JsonNode campaign) {
        if (campaign.has("maximizeConversions")) {
            MaximizeConversions.Builder strategy = MaximizeConversions.newBuilder();
            JsonNode node = campaign.get("maximizeConversions");
            if (node != null && node.hasNonNull("targetCpaMicros"))
                strategy.setTargetCpaMicros(node.get("targetCpaMicros").asLong());
            builder.setMaximizeConversions(strategy);
        } else if (campaign.has("maximizeConversionValue")) {
            builder.setMaximizeConversionValue(MaximizeConversionValue.newBuilder());
        } else if (campaign.has("targetSpend")) {
            builder.setTargetSpend(TargetSpend.newBuilder());
        }
        // else: the compiler always emits exactly one strategy key (J6 gates it); nothing to set.
    }

    private AdGroup buildAdGroup(JsonNode adGroup, String adGroupRes, String campaignRes) {
        // The P1 Search path only produces SEARCH_STANDARD ad groups (the compiler hardcodes the type).
        return AdGroup.newBuilder()
                .setResourceName(adGroupRes)
                .setName(text(adGroup, "name", ""))
                .setStatus(AdGroupStatus.PAUSED)
                .setType(AdGroupType.SEARCH_STANDARD)
                .setCampaign(campaignRes)
                .build();
    }

    private AdGroupCriterion buildKeywordCriterion(String adGroupRes, JsonNode keyword, boolean negative) {
        KeywordInfo info = KeywordInfo.newBuilder()
                .setText(text(keyword, "text", ""))
                .setMatchType(matchType(text(keyword, "matchType", "BROAD")))
                .build();
        AdGroupCriterion.Builder criterion = AdGroupCriterion.newBuilder()
                .setAdGroup(adGroupRes)
                .setKeyword(info)
                .setNegative(negative);
        // Negative criteria carry no status; only positive keywords are launched PAUSED.
        if (!negative)
            criterion.setStatus(AdGroupCriterionStatus.PAUSED);
        return criterion.build();
    }

    private AdGroupAd buildAdGroupAd(String adGroupRes, JsonNode adNode) {
        return AdGroupAd.newBuilder()
                .setAdGroup(adGroupRes)
                .setStatus(AdGroupAdStatus.PAUSED)
                .setAd(buildAd(adNode))
                .build();
    }

    private Ad buildAd(JsonNode adNode) {
        Ad.Builder ad = Ad.newBuilder();
        for (JsonNode url : arr(adNode, "finalUrls"))
            ad.addFinalUrls(url.asText());

        JsonNode rsaNode = adNode.get("responsiveSearchAd");
        if (rsaNode != null && !rsaNode.isNull()) {
            ResponsiveSearchAdInfo.Builder rsa = ResponsiveSearchAdInfo.newBuilder();
            for (JsonNode headline : arr(rsaNode, "headlines"))
                rsa.addHeadlines(AdTextAsset.newBuilder().setText(text(headline, "text", "")));
            for (JsonNode description : arr(rsaNode, "descriptions"))
                rsa.addDescriptions(AdTextAsset.newBuilder().setText(text(description, "text", "")));
            ad.setResponsiveSearchAd(rsa);
        }
        return ad.build();
    }

    private ConversionAction buildConversionAction(JsonNode node) {
        ConversionAction.Builder builder = ConversionAction.newBuilder()
                .setName(text(node, "name", ""))
                .setStatus(ConversionActionStatus.ENABLED);
        String type = text(node, "type", null);
        if (type != null)
            builder.setType(ConversionActionType.valueOf(type.trim().toUpperCase()));
        return builder.build();
    }

    // --- SPI mutations --------------------------------------------------------------------------

    public void setStatus(Token token, PlatformRef ref, RunState state) {
        long customerId = GoogleTokens.requireCustomerId(token, this.msg);
        GoogleTokens.requireLoginCustomerId(token, this.msg);
        MutateOperation op = statusOperation(customerId, ref, state);
        this.facade.mutate(token, Long.toString(customerId), List.of(op));
    }

    private MutateOperation statusOperation(long customerId, PlatformRef ref, RunState state) {
        String type = ref == null ? "" : ref.type();
        return switch (type) {
            case "campaign" -> op(CampaignOperation.newBuilder()
                    .setUpdate(Campaign.newBuilder()
                            .setResourceName(ResourceNames.campaign(customerId, idLong(ref)))
                            .setStatus(campaignStatus(state)))
                    .setUpdateMask(mask(STATUS_FIELD)).build());
            case "adGroup", "adSet" -> op(AdGroupOperation.newBuilder()
                    .setUpdate(AdGroup.newBuilder()
                            .setResourceName(ResourceNames.adGroup(customerId, idLong(ref)))
                            .setStatus(adGroupStatus(state)))
                    .setUpdateMask(mask(STATUS_FIELD)).build());
            case "ad", "adGroupAd" -> op(AdGroupAdOperation.newBuilder()
                    .setUpdate(AdGroupAd.newBuilder()
                            // ad-group-ad resource id is the composite "{adGroupId}~{adId}" carried on the ref
                            .setResourceName("customers/" + customerId + "/adGroupAds/" + ref.id())
                            .setStatus(adGroupAdStatus(state)))
                    .setUpdateMask(mask(STATUS_FIELD)).build());
            default -> this.msg.throwMessage(m -> new GenericException(HttpStatus.BAD_REQUEST, m),
                    AdzumpMessageResourceService.FIELDS_MISSING, "supported Google ref type (got '" + type + "')");
        };
    }

    public void updateBudget(Token token, PlatformRef ref, Money daily) {
        long customerId = GoogleTokens.requireCustomerId(token, this.msg);
        GoogleTokens.requireLoginCustomerId(token, this.msg);
        // Google budgets are their own resource; J8 passes the budget ref (type "campaignBudget").
        CampaignBudget budget = CampaignBudget.newBuilder()
                .setResourceName(ResourceNames.campaignBudget(customerId, idLong(ref)))
                .setAmountMicros(toMicros(daily))
                .build();
        MutateOperation op = op(CampaignBudgetOperation.newBuilder()
                .setUpdate(budget).setUpdateMask(mask("amount_micros")).build());
        this.facade.mutate(token, Long.toString(customerId), List.of(op));
    }

    public void updateBid(Token token, PlatformRef ref, BidSpec bid) {
        long customerId = GoogleTokens.requireCustomerId(token, this.msg);
        GoogleTokens.requireLoginCustomerId(token, this.msg);
        Campaign.Builder campaign = Campaign.newBuilder()
                .setResourceName(ResourceNames.campaign(customerId, idLong(ref)));
        String maskField = applyBidStrategy(campaign, bid);
        MutateOperation op = op(CampaignOperation.newBuilder()
                .setUpdate(campaign).setUpdateMask(mask(maskField)).build());
        this.facade.mutate(token, Long.toString(customerId), List.of(op));
    }

    private String applyBidStrategy(Campaign.Builder campaign, BidSpec bid) {
        String strategy = bid == null || bid.strategy() == null ? "" : bid.strategy().trim().toUpperCase();
        return switch (strategy) {
            case "MAXIMIZE_CONVERSIONS", "TARGET_CPA" -> {
                MaximizeConversions.Builder mc = MaximizeConversions.newBuilder();
                if (bid.target() != null)
                    mc.setTargetCpaMicros(toMicros(bid.target()));
                campaign.setMaximizeConversions(mc);
                yield "maximize_conversions";
            }
            case "MAXIMIZE_CONVERSION_VALUE" -> {
                campaign.setMaximizeConversionValue(MaximizeConversionValue.newBuilder());
                yield "maximize_conversion_value";
            }
            case "MAXIMIZE_CLICKS", "TARGET_SPEND" -> {
                campaign.setTargetSpend(TargetSpend.newBuilder());
                yield "target_spend";
            }
            default -> this.msg.throwMessage(m -> new GenericException(HttpStatus.BAD_REQUEST, m),
                    AdzumpMessageResourceService.FIELDS_MISSING, "supported Google bid strategy (got '" + strategy + "')");
        };
    }

    /**
     * Keyword add/remove (including negatives) on an existing ad group. The patch shape is
     * {@code {"add":[{"text","matchType","negative"}], "remove":["<criterionResourceName>"]}}.
     */
    public void mutateTargeting(Token token, PlatformRef adGroupRef, TargetingPatch patch) {
        long customerId = GoogleTokens.requireCustomerId(token, this.msg);
        GoogleTokens.requireLoginCustomerId(token, this.msg);
        String adGroupRes = ResourceNames.adGroup(customerId, idLong(adGroupRef));

        List<MutateOperation> ops = new ArrayList<>();
        JsonNode node = patch == null ? null : patch.patch();

        for (JsonNode add : arr(node, "add")) {
            boolean negative = add.path("negative").asBoolean(false);
            ops.add(op(AdGroupCriterionOperation.newBuilder()
                    .setCreate(buildKeywordCriterion(adGroupRes, add, negative)).build()));
        }
        for (JsonNode remove : arr(node, "remove"))
            ops.add(op(AdGroupCriterionOperation.newBuilder().setRemove(remove.asText()).build()));

        if (!ops.isEmpty())
            this.facade.mutate(token, Long.toString(customerId), ops);
    }

    /**
     * Creates a new RSA ad-group-ad from the compiled creative (P1 upsert = create). The creative
     * payload carries {@code finalUrls} + {@code responsiveSearchAd} (or headlines/descriptions).
     */
    public CreativeRef upsertCreative(Token token, PlatformRef adGroupRef, CompiledCreative creative) {
        long customerId = GoogleTokens.requireCustomerId(token, this.msg);
        GoogleTokens.requireLoginCustomerId(token, this.msg);
        String adGroupRes = ResourceNames.adGroup(customerId, idLong(adGroupRef));

        JsonNode payload = creative == null ? null : creative.payload();
        AdGroupAd adGroupAd = buildAdGroupAd(adGroupRes, payload == null ? emptyNode() : payload);

        MutateGoogleAdsResponse response = this.facade.mutate(token, Long.toString(customerId),
                List.of(op(AdGroupAdOperation.newBuilder().setCreate(adGroupAd).build())));

        for (MutateOperationResponse r : response.getMutateOperationResponsesList())
            if (r.hasAdGroupAdResult())
                return new CreativeRef(r.getAdGroupAdResult().getResourceName());
        return new CreativeRef(null);
    }

    // --- response parsing -----------------------------------------------------------------------

    private String extractCampaignId(MutateGoogleAdsResponse response) {
        for (MutateOperationResponse r : response.getMutateOperationResponsesList())
            if (r.hasCampaignResult())
                return GoogleTokens.idFromResourceName(r.getCampaignResult().getResourceName());
        return this.msg.throwMessage(m -> new GenericException(HttpStatus.BAD_GATEWAY, m),
                AdzumpMessageResourceService.GOOGLE_API_ERROR, "mutate response carried no campaign result");
    }

    // --- enum + value helpers -------------------------------------------------------------------

    private CampaignStatus campaignStatus(RunState state) {
        return switch (state) {
            case PAUSE -> CampaignStatus.PAUSED;
            case ACTIVE -> CampaignStatus.ENABLED;
            case ARCHIVED -> CampaignStatus.REMOVED;
        };
    }

    private AdGroupStatus adGroupStatus(RunState state) {
        return switch (state) {
            case PAUSE -> AdGroupStatus.PAUSED;
            case ACTIVE -> AdGroupStatus.ENABLED;
            case ARCHIVED -> AdGroupStatus.REMOVED;
        };
    }

    private AdGroupAdStatus adGroupAdStatus(RunState state) {
        return switch (state) {
            case PAUSE -> AdGroupAdStatus.PAUSED;
            case ACTIVE -> AdGroupAdStatus.ENABLED;
            case ARCHIVED -> AdGroupAdStatus.REMOVED;
        };
    }

    private AdvertisingChannelType channelType(String value) {
        if ("SEARCH".equalsIgnoreCase(value))
            return AdvertisingChannelType.SEARCH;
        return this.msg.throwMessage(m -> new GenericException(HttpStatus.BAD_REQUEST, m),
                AdzumpMessageResourceService.FIELDS_MISSING, "supported Google channel type (got '" + value + "')");
    }

    private KeywordMatchType matchType(String value) {
        return switch (value == null ? "" : value.trim().toUpperCase()) {
            case "EXACT" -> KeywordMatchType.EXACT;
            case "PHRASE" -> KeywordMatchType.PHRASE;
            case "BROAD", "" -> KeywordMatchType.BROAD;
            default -> this.msg.throwMessage(m -> new GenericException(HttpStatus.BAD_REQUEST, m),
                    AdzumpMessageResourceService.FIELDS_MISSING, "supported keyword match type (got '" + value + "')");
        };
    }

    private long toMicros(Money money) {
        if (money == null || money.getAmount() == null)
            this.msg.throwMessage(m -> new GenericException(HttpStatus.BAD_REQUEST, m),
                    AdzumpMessageResourceService.FIELDS_MISSING, "budget/bid amount");
        return money.getAmount().multiply(MICROS).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    private long idLong(PlatformRef ref) {
        String d = GoogleTokens.digits(ref == null ? null : ref.id());
        if (d.isEmpty())
            this.msg.throwMessage(m -> new GenericException(HttpStatus.BAD_REQUEST, m),
                    AdzumpMessageResourceService.FIELDS_MISSING, "platform ref id");
        return Long.parseLong(d);
    }

    private static String toDateTime(String value, String timeOfDay) {
        if (value == null || value.isBlank())
            return value;
        return value.length() > 10 ? value : value + " " + timeOfDay;
    }

    private static FieldMask mask(String... paths) {
        return FieldMask.newBuilder().addAllPaths(List.of(paths)).build();
    }

    private static MutateOperation op(CampaignBudgetOperation o) {
        return MutateOperation.newBuilder().setCampaignBudgetOperation(o).build();
    }

    private static MutateOperation op(CampaignOperation o) {
        return MutateOperation.newBuilder().setCampaignOperation(o).build();
    }

    private static MutateOperation op(AdGroupOperation o) {
        return MutateOperation.newBuilder().setAdGroupOperation(o).build();
    }

    private static MutateOperation op(AdGroupCriterionOperation o) {
        return MutateOperation.newBuilder().setAdGroupCriterionOperation(o).build();
    }

    private static MutateOperation op(AdGroupAdOperation o) {
        return MutateOperation.newBuilder().setAdGroupAdOperation(o).build();
    }

    private static MutateOperation op(ConversionActionOperation o) {
        return MutateOperation.newBuilder().setConversionActionOperation(o).build();
    }

    // --- JsonNode helpers -----------------------------------------------------------------------

    private static String text(JsonNode node, String field, String fallback) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? fallback : value.asText();
    }

    private static boolean bool(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value != null && value.asBoolean(false);
    }

    private static Iterable<JsonNode> arr(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value != null && value.isArray() ? value : Collections.emptyList();
    }

    private static JsonNode emptyNode() {
        return com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
    }
}
