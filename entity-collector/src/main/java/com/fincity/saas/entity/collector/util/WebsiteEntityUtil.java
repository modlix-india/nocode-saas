package com.fincity.saas.entity.collector.util;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.entity.collector.dto.EntityIntegration;
import com.fincity.saas.entity.collector.dto.EntityResponse;
import com.fincity.saas.entity.collector.dto.LeadDetails;
import com.fincity.saas.entity.collector.dto.WebsiteDetails;
import com.fincity.saas.entity.collector.fiegn.IFeignCoreService;
import com.fincity.saas.entity.collector.service.EntityCollectorLogService;
import com.fincity.saas.entity.collector.service.EntityCollectorMessageResourceService;
import org.jetbrains.annotations.NotNull;
import org.jooq.types.ULong;
import org.springframework.http.server.reactive.ServerHttpRequest;
import reactor.core.publisher.Mono;

import static com.fincity.saas.entity.collector.util.EntityUtil.fetchOAuthToken;
import static com.fincity.saas.entity.collector.util.EntityUtil.populateStaticFields;
import static com.fincity.saas.entity.collector.util.MetaEntityUtil.buildCampaignDetails;

public class WebsiteEntityUtil {

    private static final String FORWARDED_HOST = "X-Forwarded-Host";
    private static final String WEBSITE = "website";
    private static final String FACEBOOK = "facebook";
    private static final String SOCIAL_MEDIA = "socialMedia";
    private static final String WEBSITE_FORM = "contactForm";


    public static String getHost(ServerHttpRequest request) {
        return request.getHeaders().getFirst(FORWARDED_HOST);
    }

    public static Mono<EntityResponse> handleWebsiteLeadNormalization(WebsiteDetails websiteDetails, EntityIntegration integration, IFeignCoreService coreService, EntityCollectorMessageResourceService messageService, EntityCollectorLogService logService, ULong logId) {

        LeadDetails lead = buildLeadDetailsFromWebsite(websiteDetails, integration);

        EntityResponse response = new EntityResponse();
        response.setLeadDetails(lead);

        String adId = websiteDetails.getUtm_ad();

        if (adId == null || adId.isBlank()) {
            return Mono.just(response);
        }

        return FlatMapUtil.flatMapMonoWithNull(
                () -> fetchOAuthToken(coreService, integration.getClientCode(), integration.getAppCode()),
                token -> buildCampaignDetails(adId, token),
                (token, campaignDetails) -> {
                    response.setCampaignDetails(campaignDetails);
                    return Mono.just(response);
                }).switchIfEmpty(
                messageService.getMessage(EntityCollectorMessageResourceService.FAILED_NORMALIZE_ENTITY)
                        .flatMap(msg -> logService.updateOnError(logId, msg)
                                .then(Mono.empty())));

    }

    @NotNull
    private static LeadDetails buildLeadDetailsFromWebsite(WebsiteDetails details, EntityIntegration integration) {
        LeadDetails lead = new LeadDetails();

        lead.setEmail(details.getEmail());
        lead.setFullName(details.getFullName());
        lead.setPhone(details.getPhone());
        lead.setCompanyName(details.getCompanyName());
        lead.setWorkEmail(details.getWorkEmail());
        lead.setWorkPhoneNumber(details.getWorkPhoneNumber());
        lead.setJobTitle(details.getJobTitle());
        lead.setMilitaryStatus(details.getMilitaryStatus());
        lead.setRelationshipStatus(details.getRelationshipStatus());
        lead.setMaritalStatus(details.getMaritalStatus());
        lead.setGender(details.getGender());
        lead.setDob(details.getDob());
        lead.setLastName(details.getLastName());
        lead.setFirstName(details.getFirstName());
        lead.setZipCode(details.getZipCode());
        lead.setPostCode(details.getPostCode());
        lead.setCountry(details.getCountry());
        lead.setProvince(details.getProvince());
        lead.setStreetAddress(details.getStreetAddress());
        lead.setState(details.getState());
        lead.setCity(details.getCity());
        lead.setWhatsappNumber(details.getWhatsappNumber());
        lead.setSubSource(details.getSubSource());


        if (FACEBOOK.equalsIgnoreCase(details.getUtm_source())) {
            populateStaticFields(lead, integration, FACEBOOK, WEBSITE, WEBSITE_FORM);
        } else {
            populateStaticFields(lead, integration, WEBSITE, WEBSITE, Boolean.parseBoolean(lead.getSubSource()) ? lead.getSubSource() : WEBSITE_FORM);
        }

        return lead;
    }

}