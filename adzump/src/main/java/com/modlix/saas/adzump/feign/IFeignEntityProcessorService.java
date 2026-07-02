package com.modlix.saas.adzump.feign;

import java.util.List;
import java.util.Map;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * Blocking Feign client to entity-processor (J11 - leadzump-client). The Feign
 * target is "entity-processor", not "leadzump": leadzump is the CRM app served
 * by entity-processor.
 *
 * Auth model (J11 §4): every method forwards the USER'S OWN context header set
 * (Authorization + clientCode + appCode + X-Forwarded-Host + X-Forwarded-Port)
 * so entity-processor rebuilds the caller's ContextAuthentication and enforces
 * its OWN CRM authority - adzump gets no blanket CRM read. For the scheduled
 * loop (no user), a campaign-scoped service token replaces the forwarded
 * bearer (open, ties to J14).
 *
 * P0 surface: only the product reads that exist on entity-processor today.
 * The pipeline / by-ID outcomes reads are EP-side P2 additions coordinated
 * through J11 - see the TODOs below; do not invent live paths for them.
 */
@FeignClient(name = "entity-processor", contextId = "adzumpEntityProcessorService")
public interface IFeignEntityProcessorService {

    // P0-loose shape: List<Map> rather than a typed Product, so the client does
    // not lock EP's response shape before the real read lands (P2, J11 §5.4).
    @GetMapping("/api/entity/processor/products")
    List<Map<String, Object>> listProducts(
            @RequestHeader("Authorization") String authorization,
            @RequestHeader("clientCode") String clientCode,
            @RequestHeader("appCode") String appCode,
            @RequestHeader("X-Forwarded-Host") String forwardedHost,
            @RequestHeader("X-Forwarded-Port") String forwardedPort);

    @GetMapping("/api/entity/processor/products/{id}")
    Map<String, Object> getProduct(
            @RequestHeader("Authorization") String authorization,
            @RequestHeader("clientCode") String clientCode,
            @RequestHeader("appCode") String appCode,
            @RequestHeader("X-Forwarded-Host") String forwardedHost,
            @RequestHeader("X-Forwarded-Port") String forwardedPort,
            @PathVariable("id") String id);

    // TODO(P2, J11 §9 - EP-side addition, coordinate before wiring): pipeline
    // (stages/statuses) per product template. The by-template read does not
    // exist on entity-processor yet; define the endpoint there first.
    // ProductTemplatePipeline getPipeline(<user-context header set>,
    //         @PathVariable("templateId") String templateId);

    // TODO(P2, J11 §9 - EP-side addition, coordinate before wiring): the NEW
    // lean by-ID outcomes read - joins CRM outcomes by ad-grain ID, per product
    // template, timezone + date-range parameterized (NOT the existing
    // analytics/campaigns/tree). Shape is fixed by OutcomeQuery/CrmOutcomes
    // (model/leadzump); the EP endpoint lands with J10 (P2).
    // CrmOutcomes getOutcomes(<user-context header set>,
    //         @RequestBody OutcomeQuery query);

    // TODO(P2, J9 write-back): patchProductProfile(<user-context header set>,
    //         @PathVariable String productId, @RequestBody JsonNode profilePatch);

    // TODO(P2, J8/J22): get/putCampaignProductLink - the CampaignProductLink
    // entity lives in entity-processor (not adzump J1); read/write through here
    // once the EP endpoints are defined.
}
