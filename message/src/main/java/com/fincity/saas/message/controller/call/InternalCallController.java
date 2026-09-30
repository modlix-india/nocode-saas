package com.fincity.saas.message.controller.call;

import com.fincity.saas.message.model.common.MessageAccess;
import com.fincity.saas.message.model.request.call.BrowserDialRequest;
import com.fincity.saas.message.model.request.call.CallRequest;
import com.fincity.saas.message.model.request.call.IncomingCallRequest;
import com.fincity.saas.message.service.call.CallService;
import com.fincity.saas.message.service.call.provider.telecmi.TelecmiCallService;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * The service-to-service call operations, for whichever provider the named connection uses. The provider-neutral
 * twin of the {@code /api/message/call/exotel/internal} routes, which stay for callers already deployed.
 *
 * <p>Behind {@code /internal} deliberately: permitAll, kept off the internet by nginx, and none of these checks
 * whether the call may be made; entity-processor does, against the deal. Not a {@code BaseUpdatableController},
 * whose generic data API would expose every call row in the tenant.
 */
@RestController
@RequestMapping("/api/message/call/internal")
public class InternalCallController {

    /** The fields of a flow verification request. */
    public static final String FLOW_TOKEN = "token";

    public static final String FLOW_APP_ID = "appId";

    private final CallService callService;
    private final TelecmiCallService telecmiCallService;

    public InternalCallController(CallService callService, TelecmiCallService telecmiCallService) {
        this.callService = callService;
        this.telecmiCallService = telecmiCallService;
    }

    /** Answers the provider's inbound request with whom to ring, in that provider's reply format. */
    @PostMapping("/connect")
    public Mono<Object> connectCall(
            @RequestHeader("appCode") String appCode,
            @RequestHeader("clientCode") String clientCode,
            @RequestBody IncomingCallRequest request) {
        return this.callService.connectCall(appCode, clientCode, request);
    }

    /** Rings the agent's browser, then the deal's customer. Returns the provider-shaped call. */
    @PostMapping("/browser-dial")
    public Mono<ResponseEntity<Object>> browserDialInternal(
            @RequestParam String appCode, @RequestParam String clientCode, @RequestBody BrowserDialRequest request) {
        return this.callService
                .browserDialInternal(appCode, clientCode, request)
                .map(ResponseEntity::ok);
    }

    /**
     * Whether an inbound TeleCMI HTTP flow request is this tenant's, by its {@code ?t=} token and app id; 401 when
     * either is wrong. Separate from {@code /connect} because the caller asks before it looks up or creates a deal.
     */
    @PostMapping("/telecmi/verify")
    public Mono<ResponseEntity<Boolean>> verifyTelecmiFlow(
            @RequestHeader("appCode") String appCode,
            @RequestHeader("clientCode") String clientCode,
            @RequestBody Map<String, String> request) {
        return this.telecmiCallService
                .verifyInboundFlow(
                        MessageAccess.of(appCode, clientCode, true), request.get(FLOW_TOKEN), request.get(FLOW_APP_ID))
                .map(ResponseEntity::ok);
    }

    /** Places a call through the provider. Returns the provider-shaped call. */
    @PostMapping("/make")
    public Mono<ResponseEntity<Object>> makeCallInternal(
            @RequestParam String appCode,
            @RequestParam String clientCode,
            @RequestBody CallRequest request,
            @RequestParam(required = false, defaultValue = "entity-processor") String ownerService) {
        return this.callService
                .makeCallInternal(appCode, clientCode, request, ownerService)
                .map(ResponseEntity::ok);
    }
}
