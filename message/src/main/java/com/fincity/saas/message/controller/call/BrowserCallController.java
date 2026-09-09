package com.fincity.saas.message.controller.call;

import com.fincity.saas.message.model.request.call.ProvisionAgentRequest;
import com.fincity.saas.message.model.response.call.BrowserCallStatus;
import com.fincity.saas.message.model.response.call.BrowserCallToken;
import com.fincity.saas.message.service.call.CallService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * What an agent's browser softphone calls: am I set up, give me a credential, place this call.
 *
 * <p>"browser" rather than "webrtc" on purpose. The capability is that an agent can take calls in a
 * browser; WebRTC is one transport for it, and naming the route after the transport would date the
 * moment a provider used something else.
 *
 * <p>Every route here takes the agent from the authenticated token, never from the request. A
 * {@code userId} field on any of these would let one agent mint another's SIP credentials or dial
 * as them. Sits under a literal path segment rather than {@code /api/message/call/{id}} so it can
 * never be shadowed by the generic {@code {id}} route on {@code CallController}.
 */
@RestController
@RequestMapping("/api/message/call/browser")
public class BrowserCallController {

    private final CallService callService;

    public BrowserCallController(CallService callService) {
        this.callService = callService;
    }

    /**
     * Whether this agent can take calls in the browser, and under which provider.
     *
     * <p>Called on page load, before any token is requested. A database read with no provider round
     * trip, so it is cheap enough to run every time. The UI binds the phone's visibility to it,
     * which is how an unprovisioned agent is told apart from a broken integration.
     */
    @GetMapping("/status")
    public Mono<ResponseEntity<BrowserCallStatus>> status(
            @RequestParam String connectionName,
            @RequestParam(required = false, defaultValue = "false") boolean verify) {
        return this.callService.browserStatus(connectionName, verify).map(ResponseEntity::ok);
    }

    /**
     * A short-lived credential for this agent's softphone.
     *
     * <p>Never cached and never shared: one token handed to two agents is a cross-agent credential
     * leak.
     *
     * <p><b>There is no rate limit on this endpoint, and there is none anywhere in this service.</b>
     * Every call reaches the provider, so a page in a reload loop burns the tenant's provider quota
     * and takes calling down for everyone in it — an authenticated agent can do that today without
     * meaning to. Stated as absent rather than as an intention, because a comment describing a limit
     * that does not exist reads as one that does.
     */
    @PostMapping("/token")
    public Mono<ResponseEntity<BrowserCallToken>> token(@RequestBody ProvisionAgentRequest request) {
        return this.callService.browserToken(request.getConnectionName()).map(ResponseEntity::ok);
    }

    // No dial route here, deliberately. Placing a call needs a customer number, and neither form a
    // browser could send is safe: a raw number lets an agent ring anyone on the tenant's account,
    // and a ticket id could only be resolved by an internal lookup that ignores deal visibility.
    // Outbound therefore starts in entity-processor, which reads the deal under the caller's own
    // access, and reaches this service on an internal route nginx keeps off the internet.
}
