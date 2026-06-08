package com.fincity.saas.entity.processor.controller;

import com.fincity.saas.entity.processor.controller.base.BaseUpdatableController;
import com.fincity.saas.entity.processor.dao.ConversionActionMappingDAO;
import com.fincity.saas.entity.processor.dto.ConversionActionMapping;
import com.fincity.saas.entity.processor.jooq.tables.records.EntityProcessorConversionActionMappingRecord;
import com.fincity.saas.entity.processor.model.discovery.DiscoveredConversionAction;
import com.fincity.saas.entity.processor.model.request.ApplyFunnelMappingRequest;
import com.fincity.saas.entity.processor.model.request.ConversionActionMappingRequest;
import com.fincity.saas.entity.processor.model.request.CreateGoogleConversionActionRequest;
import com.fincity.saas.entity.processor.model.request.SeedConversionDefaultsRequest;
import com.fincity.saas.entity.processor.service.ConversionActionMappingService;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("api/entity/processor/conversion/action/mappings")
public class ConversionActionMappingController
        extends BaseUpdatableController<
                EntityProcessorConversionActionMappingRecord,
                ConversionActionMapping,
                ConversionActionMappingDAO,
                ConversionActionMappingService> {

    @PostMapping(REQ_PATH)
    public Mono<ResponseEntity<ConversionActionMapping>> createRequest(
            @RequestBody ConversionActionMappingRequest request) {
        return this.service.createRequest(request).map(ResponseEntity::ok);
    }

    @PostMapping("/seed/defaults")
    public Mono<ResponseEntity<List<ConversionActionMapping>>> seedDefaults(
            @RequestBody SeedConversionDefaultsRequest request) {
        return this.service.seedDefaults(request).map(ResponseEntity::ok);
    }

    @PostMapping("/funnel/apply")
    public Mono<ResponseEntity<Map<String, Object>>> applyFunnel(@RequestBody ApplyFunnelMappingRequest request) {
        return this.service.applyFunnel(request).map(ResponseEntity::ok);
    }

    /** Lists Google Ads conversion actions in the client's connected account for the mapping picker. */
    @GetMapping("/google/conversion-actions")
    public Mono<ResponseEntity<List<DiscoveredConversionAction>>> listGoogleConversionActions(
            @RequestParam(required = false) String customerId,
            @RequestParam(required = false) String loginCustomerId) {
        return this.service.listGoogleConversionActions(customerId, loginCustomerId).map(ResponseEntity::ok);
    }

    /** Creates a Google Ads conversion action in the client's account (fresh-client provisioning). */
    @PostMapping("/google/conversion-actions")
    public Mono<ResponseEntity<DiscoveredConversionAction>> createGoogleConversionAction(
            @RequestBody CreateGoogleConversionActionRequest request) {
        return this.service.createGoogleConversionAction(request).map(ResponseEntity::ok);
    }

    /**
     * Soft-deletes a Google Ads conversion action by resource name (sets status
     * to REMOVED on Google's side). {@code resourceName} is the full Google
     * resource path (e.g.
     * {@code customers/4220436668/conversionActions/7640505544}). Pass it
     * URL-encoded as a query param.
     */
    @DeleteMapping("/google/conversion-actions")
    public Mono<ResponseEntity<Void>> removeGoogleConversionAction(
            @RequestParam String resourceName,
            @RequestParam(required = false) String customerId,
            @RequestParam(required = false) String loginCustomerId) {
        return this.service
                .removeGoogleConversionAction(resourceName, customerId, loginCustomerId)
                .<ResponseEntity<Void>>thenReturn(ResponseEntity.noContent().build());
    }
}
