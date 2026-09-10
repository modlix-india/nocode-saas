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
 * Providers that can place and receive calls inside a browser.
 *
 * <p>Deliberately separate from {@link ICallService} rather than a set of {@code default} methods on
 * it. {@code ICallService} returns {@code Mono.empty()} for what a provider does not support, which
 * is indistinguishable from a genuine miss; an admin who calls provisioning against a provider that
 * cannot do browser calling deserves a real error, not an empty body. A provider gains the
 * capability by implementing this interface and loses it by not implementing it, so it cannot be
 * half-registered.
 */
public interface IBrowserCallService {

    ConnectionSubType getConnectionSubType();

    /** Registers this tenant's integration app with the provider. Idempotent. */
    Mono<CallAppStatus> initializeApp(MessageAccess access, Connection connection);

    /** Maps one agent to a browser-reachable endpoint. Idempotent. */
    Mono<ProvisionedAgent> provisionAgent(MessageAccess access, Connection connection, ProvisionAgentRequest request);

    /**
     * Removes this tenant's integration app at the provider and forgets it locally.
     *
     * <p>The inverse of {@link #initializeApp}, and only possible for an app this service created:
     * deleting one requires credentials the provider issues once, at creation.
     */
    Mono<Boolean> teardownApp(MessageAccess access, Connection connection);

    /**
     * Every agent provisioned on this connection, one entry each.
     *
     * <p>Returns the consolidated view rather than the stored rows. A provider keeps one row per
     * destination because ringing is sequential, and a listing built from those shows the same agent
     * once per destination — so the collapsing belongs behind this interface, where each provider
     * knows what its own endpoint types mean.
     */
    Flux<ProvisionedAgent> getAgentEndpoints(MessageAccess access, Connection connection);

    /** Whether this tenant's app exists at the provider. Answered locally, for a settings screen. */
    Mono<CallAppStatus> callAppStatus(MessageAccess access);

    /**
     * Retires an agent's endpoints.
     *
     * <p>Implementations must revoke at the provider as well as locally. Clearing our own rows only
     * stops this service minting new tokens; a token already in a browser keeps working until the
     * provider stops honouring it.
     */
    Mono<Integer> deactivateAgent(MessageAccess access, Connection connection, ULong userId);

    /** A short-lived credential for one agent's browser. Never cached, never shared. */
    Mono<BrowserCallToken> generateBrowserToken(MessageAccess access, Connection connection, ULong userId);

    /**
     * Whether this agent can take calls in the browser, and under which provider.
     *
     * <p>{@code verifyWithProvider} chooses how much the answer is worth. False reads local state
     * only — cheap enough for every page load, and blind to anything the provider changed since
     * provisioning. True asks the provider, which is the only way to know a call would actually
     * connect: a softphone registers successfully even for an agent who cannot originate at all.
     */
    Mono<BrowserCallStatus> browserCallStatus(
            MessageAccess access, Connection connection, ULong userId, boolean verifyWithProvider);
}
