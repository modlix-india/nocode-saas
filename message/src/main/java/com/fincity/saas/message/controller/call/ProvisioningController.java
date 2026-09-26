package com.fincity.saas.message.controller.call;

import com.fincity.saas.message.model.request.call.ProvisionAgentRequest;
import com.fincity.saas.message.model.response.call.CallAppStatus;
import com.fincity.saas.message.model.response.call.ProvisionedAgent;
import com.fincity.saas.message.service.call.CallService;
import java.math.BigInteger;
import java.util.List;
import org.jooq.types.ULong;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * Setting up browser calling for a tenant, and mapping its agents.
 *
 * <p>Hand-written rather than extending {@code BaseUpdatableController}, and that is deliberate.
 * The base class would bring a full generic data API with it — {@code POST /}, {@code /query},
 * {@code /eager/query}, {@code DELETE /{id}} — scoped only by app and client, so any authenticated
 * user in the tenant could read every row. Both tables behind these routes hold provider
 * credentials, and the eager paths return {@code rec.intoMap()} straight off the JOOQ record, which
 * no Jackson annotation on the DTO can filter. Exposing only these six named operations is the control;
 * the {@code @JsonIgnore}s on the DTOs are defence in depth behind it.
 *
 * <p>No security annotations here. Authorization lives on {@link CallService}, per this codebase's
 * convention.
 */
@RestController
@RequestMapping("/api/message/call/provisioning")
public class ProvisioningController {

    private final CallService callService;

    public ProvisioningController(CallService callService) {
        this.callService = callService;
    }

    /** Registers the tenant's integration app with the provider. Idempotent. */
    @PostMapping("/initialize")
    public Mono<ResponseEntity<CallAppStatus>> initialize(@RequestBody ProvisionAgentRequest request) {
        return this.callService.initializeCallApp(request.getConnectionName()).map(ResponseEntity::ok);
    }

    /** Maps one agent to a browser-reachable endpoint. Idempotent. */
    @PostMapping("/agent")
    public Mono<ResponseEntity<ProvisionedAgent>> provisionAgent(@RequestBody ProvisionAgentRequest request) {
        return this.callService.provisionAgent(request).map(ResponseEntity::ok);
    }

    /**
     * Removes the tenant's integration app at the provider, and the local rows with it.
     *
     * <p>Destructive: every agent on this connection loses browser calling. Only works for an app
     * this service created, because deleting one needs the secret the provider issues once.
     */
    @DeleteMapping("/app")
    public Mono<ResponseEntity<Boolean>> teardown(@RequestParam(name = "connectionName") String connectionName) {
        return this.callService.teardownCallApp(connectionName).map(ResponseEntity::ok);
    }

    /**
     * Whether calling is set up for this tenant, for a settings screen to render a state from.
     *
     * <p>Always a {@code 200}. Not initialised comes back as {@code initialized: false} rather than
     * an empty {@code 204}, so a caller has something to bind to and does not have to infer meaning
     * from a status code.
     *
     * <p>Answered from our own row, so it is cheap enough to load with the page. It reports that the
     * app was registered, not that the provider still holds it.
     */
    @GetMapping("/app")
    public Mono<ResponseEntity<CallAppStatus>> appStatus(@RequestParam(name = "connectionName") String connectionName) {
        return this.callService.callAppStatus(connectionName).map(ResponseEntity::ok);
    }

    /** Every agent provisioned on a connection, one entry each rather than one per destination. */
    @GetMapping("/agents")
    public Mono<ResponseEntity<List<ProvisionedAgent>>> agents(
            @RequestParam(name = "connectionName") String connectionName) {
        return this.callService.getAgentEndpoints(connectionName).collectList().map(ResponseEntity::ok);
    }

    /**
     * Retires an agent's endpoints.
     *
     * <p>Belongs in offboarding. Note it stops new tokens being minted but does not, on its own,
     * end a session already running in a browser.
     */
    @DeleteMapping("/agent/{userId}")
    public Mono<ResponseEntity<Integer>> deactivateAgent(
            @PathVariable(name = "userId") BigInteger userId,
            @RequestParam(name = "connectionName") String connectionName) {
        return this.callService
                .deactivateAgent(connectionName, ULong.valueOf(userId))
                .map(ResponseEntity::ok);
    }
}
