package com.fincity.saas.message.controller.call.provider.exotel;

import com.fincity.saas.message.dto.call.Call;
import com.fincity.saas.message.dto.call.provider.exotel.ExotelCall;
import com.fincity.saas.message.model.request.call.CallRequest;
import com.fincity.saas.message.service.call.provider.exotel.ExotelCallService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Exotel's public make and its internal make, and nothing else.
 *
 * <p>Hand-written rather than a {@code BaseUpdatableController}: the generic data API would let any
 * user in the tenant read and rewrite every call row, recordings and customer numbers included.
 * The CRM reads call history through entity-processor, which applies deal visibility.
 */
@RestController
@RequestMapping("/api/message/call/exotel")
public class ExotelCallController {

    private final ExotelCallService service;

    public ExotelCallController(ExotelCallService service) {
        this.service = service;
    }

    @PostMapping("/make")
    public Mono<ResponseEntity<Call>> makeCall(@RequestBody CallRequest request) {
        return this.service.makeCall(request).map(ResponseEntity::ok);
    }

    /**
     * Places a call for a service that has already checked the caller may make it. Kept for
     * entity-processor builds older than {@code /api/message/call/internal/make}; behind
     * {@code /internal}, which nginx blocks, because it places billable calls with no check of its own.
     */
    @PostMapping("/internal/make")
    public Mono<ResponseEntity<ExotelCall>> makeCallInternal(
            @RequestParam String appCode,
            @RequestParam String clientCode,
            @RequestBody CallRequest request,
            @RequestParam(required = false, defaultValue = "entity-processor") String ownerService) {
        return this.service
                .makeCallInternal(appCode, clientCode, request, ownerService)
                .map(ResponseEntity::ok);
    }
}
