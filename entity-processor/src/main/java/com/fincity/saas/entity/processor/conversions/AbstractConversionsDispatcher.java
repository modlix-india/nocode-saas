package com.fincity.saas.entity.processor.conversions;

import com.fincity.saas.entity.processor.dto.Campaign;
import com.fincity.saas.entity.processor.dto.ConversionActionMapping;
import com.fincity.saas.entity.processor.dto.ConversionEvent;
import com.fincity.saas.entity.processor.dto.Ticket;
import com.fincity.saas.entity.processor.enums.CampaignPlatform;
import reactor.core.publisher.Mono;

/**
 * Platform-specific dispatcher contract. Each impl knows how to convert a
 * {@link ConversionEvent} + supporting context into the platform's wire payload
 * and POST it to the platform's Conversions API. Returns a {@link DispatchResult}
 * indicating success/failure plus a snapshot of the platform response that the
 * outbox stores for diagnostics.
 */
public abstract class AbstractConversionsDispatcher {

    public abstract CampaignPlatform getPlatform();

    public abstract Mono<DispatchResult> dispatch(
            ConversionEvent event,
            ConversionActionMapping mapping,
            Ticket ticket,
            Campaign campaign,
            String accessToken);
}
