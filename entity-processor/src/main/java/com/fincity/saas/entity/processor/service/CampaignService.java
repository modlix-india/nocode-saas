package com.fincity.saas.entity.processor.service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.entity.processor.dao.CampaignDAO;
import com.fincity.saas.entity.processor.dto.Campaign;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorCampaignsRecord;
import com.fincity.saas.entity.processor.model.request.CampaignRequest;
import com.fincity.saas.entity.processor.service.base.BaseUpdatableService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class CampaignService extends BaseUpdatableService<EntityProcessorCampaignsRecord, Campaign, CampaignDAO> {

    private static final String CAMPAIGN_CACHE = "campaign";

    private final ProductService productService;

    @Autowired
    public CampaignService(ProductService productService) {
        this.productService = productService;
    }

    @Override
    protected String getCacheName() {
        return CAMPAIGN_CACHE;
    }

    @Override
    protected boolean canOutsideCreate() {
        return false;
    }

    public Mono<Campaign> create(CampaignRequest campaignRequest) {

        return FlatMapUtil.flatMapMono(

                        this::hasAccess,

                        access -> this.productService.readIdentityWithAccess(access, campaignRequest.getProductId()),

                        (access, product) -> super.createInternal(
                                access, Campaign.of(campaignRequest).setProductId(product.getId())))
                .contextWrite(Context.of(LogUtil.METHOD_NAME, "ProductService.create[CampaignRequest]"));
    }
}
