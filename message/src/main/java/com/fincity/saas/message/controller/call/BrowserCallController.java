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
 * What an agent's browser softphone calls. Every route takes the agent from the authenticated token, never the
 * request, or one agent could mint another's SIP credentials. A literal path segment, so the generic {@code {id}}
 * route on {@code CallController} cannot shadow it.
 */
@RestController
@RequestMapping("/api/message/call/browser")
public class BrowserCallController {

    private final CallService callService;

    public BrowserCallController(CallService callService) {
        this.callService = callService;
    }

    /**
     * Whether this agent can take calls in the browser, read on page load. Without {@code connectionName} it
     * answers for, and names, the connection this agent is provisioned on.
     */
    @GetMapping("/status")
    public Mono<ResponseEntity<BrowserCallStatus>> status(
            @RequestParam(required = false) String connectionName,
            @RequestParam(required = false, defaultValue = "false") boolean verify) {
        return this.callService.browserStatus(connectionName, verify).map(ResponseEntity::ok);
    }

    /**
     * A short-lived credential for this agent's softphone, never cached or shared.
     *
     * <p>There is no rate limit here or anywhere in this service. Every call reaches the provider, so a page in a
     * reload loop can burn the tenant's provider quota.
     */
    @PostMapping("/token")
    public Mono<ResponseEntity<BrowserCallToken>> token(@RequestBody ProvisionAgentRequest request) {
        return this.callService.browserToken(request.getConnectionName()).map(ResponseEntity::ok);
    }

    // No dial route here, deliberately: a raw number would let an agent ring anyone. Outbound starts in
    // entity-processor, which checks the deal under the caller's access, then uses an internal route nginx blocks.
}
