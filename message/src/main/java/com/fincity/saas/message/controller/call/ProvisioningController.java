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
 * Setting up browser calling for a tenant, and mapping its agents. Deliberately not a
 * {@code BaseUpdatableController}: its generic query routes would let any tenant user read rows holding provider
 * credentials, and the eager paths return {@code rec.intoMap()}, which no {@code @JsonIgnore} filters.
 * Authorization lives on {@link CallService}.
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

    /** Removes the tenant's integration app at the provider and locally; every agent loses browser calling. */
    @DeleteMapping("/app")
    public Mono<ResponseEntity<Boolean>> teardown(@RequestParam(name = "connectionName") String connectionName) {
        return this.callService.teardownCallApp(connectionName).map(ResponseEntity::ok);
    }

    /**
     * Whether calling is set up for this tenant; always a {@code 200}. Reports that the app was registered, not
     * that the provider still holds it.
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

    /** Retires an agent's endpoints, for offboarding. */
    @DeleteMapping("/agent/{userId}")
    public Mono<ResponseEntity<Integer>> deactivateAgent(
            @PathVariable(name = "userId") BigInteger userId,
            @RequestParam(name = "connectionName") String connectionName) {
        return this.callService
                .deactivateAgent(connectionName, ULong.valueOf(userId))
                .map(ResponseEntity::ok);
    }
}
