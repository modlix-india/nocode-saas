package com.fincity.saas.message.controller.call.provider.exotel;

import com.fincity.saas.message.controller.base.BaseUpdatableController;
import com.fincity.saas.message.dao.call.provider.exotel.ExotelDAO;
import com.fincity.saas.message.dto.call.Call;
import com.fincity.saas.message.dto.call.provider.exotel.ExotelCall;
import com.fincity.saas.message.jooq.tables.records.MessageExotelCallsRecord;
import com.fincity.saas.message.model.request.call.CallRequest;
import com.fincity.saas.message.model.request.call.IncomingCallRequest;
import com.fincity.saas.message.model.response.call.provider.exotel.ExotelConnectAppletResponse;
import com.fincity.saas.message.service.call.provider.exotel.ExotelCallService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/message/call/exotel")
public class ExotelCallController
        extends BaseUpdatableController<MessageExotelCallsRecord, ExotelCall, ExotelDAO, ExotelCallService> {

    @PostMapping("/connect")
    public Mono<ExotelConnectAppletResponse> connectCall(
            @RequestHeader("appCode") String appCode,
            @RequestHeader("clientCode") String clientCode,
            @RequestBody IncomingCallRequest request) {
        return this.service.connectCall(appCode, clientCode, request);
    }

    @PostMapping("/make")
    public Mono<ResponseEntity<Call>> makeCall(@RequestBody CallRequest request) {
        return this.service.makeCall(request).map(ResponseEntity::ok);
    }

    /**
     * Places a call for a service that has already checked the caller may make it.
     *
     * <p>Returns the provider-shaped call rather than the thin wrapper above, because the caller
     * needs the provider's Sid to key its own record on, and its callbacks are routed back by the
     * owner recorded here.
     *
     * <p>Behind {@code /internal}: permitAll at the application layer, blocked by nginx. That is
     * load-bearing rather than incidental, because this endpoint places real, billable calls with no
     * check of its own.
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
