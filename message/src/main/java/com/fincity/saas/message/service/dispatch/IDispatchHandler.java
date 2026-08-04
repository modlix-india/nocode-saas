package com.fincity.saas.message.service.message.provider.whatsapp.dispatch;

import com.fincity.saas.message.model.request.message.provider.whatsapp.WhatsappInboundDispatch;
import reactor.core.publisher.Mono;

/**
 * One consuming service that can own WhatsApp numbers.
 *
 * <p>A number records which service owns conversations on it ({@code OWNER_SERVICE} on the phone
 * number), and inbound events route to the matching handler. That is what keeps this service a
 * provider adapter: it knows a number belongs to someone, not what the conversation means.
 *
 * <p>Implementations are typed feign clients, so <b>adding a consumer means changing and
 * redeploying this service</b>. Chosen with that cost understood: the alternative, dispatching by
 * service name through Eureka against a fixed contract, needs no change here but gives up compile
 * time checking on a contract that spans two services. Worth revisiting if a third consumer ever
 * appears.
 *
 * <p>Must throw rather than swallow. The caller keeps its outbox row on failure and retries with
 * backoff, so an error absorbed here is a message deleted from the queue and never delivered.
 */
public interface IWhatsappInboundHandler {

    /** Eureka service id, matched against a phone number's {@code OWNER_SERVICE}. */
    String getServiceName();

    Mono<Void> handle(String appCode, String clientCode, WhatsappInboundDispatch dispatch);
}
