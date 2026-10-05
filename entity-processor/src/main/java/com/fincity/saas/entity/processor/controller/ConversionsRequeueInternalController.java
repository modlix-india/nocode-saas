package com.fincity.saas.entity.processor.controller;

import com.fincity.saas.entity.processor.service.ConversionEventService;
import java.util.HashMap;
import java.util.Map;
import org.jooq.types.ULong;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Puts a campaign's SKIPPED conversion events back in the retry pool, after the thing that made
 * them terminal has been fixed.
 *
 * <p>The counterpart to terminal classification. Skipping a conversion whose campaign has no Meta
 * pixel id is right — retrying it hourly for 98 days is what took production down on 2026-10-05 —
 * but the conversion itself is still valid, and once the pixel id is set it can be delivered. Since
 * {@code findDispatchable} selects PENDING and FAILED only, nothing would ever pick those rows up
 * again. This is how they come back.
 *
 * <p>The workflow it exists for:
 *
 * <ol>
 *   <li>a campaign is created without a pixel id, so its conversions skip and say why;
 *   <li>somebody sets the pixel id on the campaign;
 *   <li>this is called with that campaign id, and the historical conversions queue for delivery.
 * </ol>
 *
 * <p>Path sits under {@code conversions/internal}, which {@code ProcessorConfiguration} already
 * allow-lists, so it is reachable by the worker and by an operator inside the network without a
 * user JWT — and is kept off the public gateway by nginx, like every other {@code /internal/}
 * route.
 *
 * <p>Deliberately NOT automatic on a pixel-id change. Requeueing can re-send conversions that are
 * months old, and whether that is wanted depends on the advertiser's attribution window and on
 * whether the same conversions were imported another way in the meantime. That is a judgement for
 * whoever fixed the campaign, not a side effect of editing a field.
 */
@RestController
@RequestMapping("api/entity/processor/conversions/internal")
public class ConversionsRequeueInternalController {

    private final ConversionEventService eventService;

    public ConversionsRequeueInternalController(ConversionEventService eventService) {
        this.eventService = eventService;
    }

    @PostMapping("/requeue/campaign/{campaignId}")
    public Mono<ResponseEntity<Map<String, Object>>> requeueForCampaign(@PathVariable ULong campaignId) {

        return this.eventService.requeueSkippedForCampaign(campaignId).map(moved -> {
            Map<String, Object> body = new HashMap<>();
            body.put("campaignId", campaignId.toString());
            body.put("requeued", moved);
            // Said plainly so an operator knows whether to go and look at the campaign again,
            // rather than reading a bare zero as success.
            body.put(
                    "message",
                    moved == 0
                            ? "No skipped conversion events found for this campaign"
                            : "Queued " + moved + " skipped conversion event(s) for the next drain");
            return ResponseEntity.ok(body);
        });
    }
}
