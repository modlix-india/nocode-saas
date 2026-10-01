package com.fincity.saas.message.service.call;

import com.fincity.saas.message.model.common.MessageAccess;
import com.fincity.saas.message.model.request.call.ProvisionAgentRequest;
import com.fincity.saas.message.model.response.call.BrowserCallStatus;
import com.fincity.saas.message.model.response.call.BrowserCallToken;
import com.fincity.saas.message.model.response.call.CallAppStatus;
import com.fincity.saas.message.model.response.call.ProvisionedAgent;
import com.fincity.saas.message.oserver.core.document.Connection;
import com.fincity.saas.message.oserver.core.enums.ConnectionSubType;
import org.jooq.types.ULong;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Providers that can place and receive calls inside a browser. Separate from {@link ICallService}, whose
 * unsupported operations return empty, so provisioning against a provider without browser calling is a real error.
 */
public interface IBrowserCallService {

    ConnectionSubType getConnectionSubType();

    /** Registers this tenant's integration app with the provider. Idempotent. */
    Mono<CallAppStatus> initializeApp(MessageAccess access, Connection connection);

    /** Maps one agent to a browser-reachable endpoint. Idempotent. */
    Mono<ProvisionedAgent> provisionAgent(MessageAccess access, Connection connection, ProvisionAgentRequest request);

    /**
     * Removes this tenant's integration app at the provider and locally. Only possible for an app this service
     * created: deleting one needs credentials the provider issues once, at creation.
     */
    Mono<Boolean> teardownApp(MessageAccess access, Connection connection);

    /** Every agent provisioned on this connection, one entry each, not one per stored destination row. */
    Flux<ProvisionedAgent> getAgentEndpoints(MessageAccess access, Connection connection);

    /** Whether this tenant's app exists at the provider. Answered locally, for a settings screen. */
    Mono<CallAppStatus> callAppStatus(MessageAccess access);

    /**
     * Retires an agent's endpoints. Implementations must revoke at the provider too: a token already in a browser
     * keeps working until the provider stops honouring it.
     */
    Mono<Integer> deactivateAgent(MessageAccess access, Connection connection, ULong userId);

    /** A short-lived credential for one agent's browser. Never cached, never shared. */
    Mono<BrowserCallToken> generateBrowserToken(MessageAccess access, Connection connection, ULong userId);

    /**
     * Whether this agent can take calls in the browser. With {@code verifyWithProvider} false it reads local state
     * only; true asks the provider, the only way to know a call would connect, since a softphone registers even
     * for an agent who cannot originate.
     */
    Mono<BrowserCallStatus> browserCallStatus(
            MessageAccess access, Connection connection, ULong userId, boolean verifyWithProvider);

    /**
     * Rings the agent's browser and then the customer, returning the provider-shaped call. Neither agent nor
     * number is verified here, as this service has no view of deals: the caller must pass the deal's number.
     */
    Mono<?> browserDialInternal(
            String appCode, String clientCode, String connectionName, ULong userId, String toNumber);
}
