package com.fincity.saas.message.service.dispatch;

import com.fincity.saas.message.enums.dispatch.DispatchChannel;
import java.util.Map;
import reactor.core.publisher.Mono;

/**
 * One consuming service, for one channel.
 *
 * <p>Something on this side records which service owns an event ({@code OWNER_SERVICE}, on the phone
 * number for WhatsApp and on the call itself for Exotel), and events route to the handler matching
 * that name and channel. That is what keeps this service a provider adapter: it knows an event
 * belongs to someone, not what the conversation or the call means.
 *
 * <p>Implementations are typed feign clients, so <b>adding a consumer means changing and redeploying
 * this service</b>. Chosen with that cost understood: the alternative, dispatching by service name
 * through Eureka against a fixed contract, needs no change here but gives up compile-time checking
 * on a contract that spans two services. Worth revisiting if a third consumer ever appears.
 *
 * <p>The payload crosses as a {@link Map} rather than a typed object, and that is not a retreat from
 * the above. It is what lets one dispatcher and one sweeper serve every channel: the implementation
 * converts to its own request type before calling its feign method, so the cross-service contract
 * stays typed at the only place that has to know it.
 *
 * <p>Must throw rather than swallow. The caller keeps its outbox row on failure and retries with
 * backoff, so an error absorbed here is an event deleted from the queue and never delivered.
 */
public interface IDispatchHandler {

    /** Eureka service id, matched against the event's recorded {@code OWNER_SERVICE}. */
    String getServiceName();

    /** Which family of events this handler accepts. */
    DispatchChannel getChannel();

    Mono<Void> handle(String appCode, String clientCode, Map<String, Object> payload);

    /** Registry key, so one map can hold every handler across every channel. */
    default String registryKey() {
        return key(this.getChannel(), this.getServiceName());
    }

    static String key(DispatchChannel channel, String serviceName) {
        return channel + "::" + serviceName;
    }
}
