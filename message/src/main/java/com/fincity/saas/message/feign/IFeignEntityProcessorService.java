package com.fincity.saas.message.feign;

import com.fincity.saas.message.model.request.dispatch.CallEventDispatch;
import com.fincity.saas.message.model.request.message.provider.whatsapp.WhatsappInboundDispatch;
import com.fincity.saas.message.oserver.entity.processor.model.Product;
import com.fincity.saas.message.oserver.entity.processor.model.Ticket;
import java.math.BigInteger;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import reactivefeign.spring.config.ReactiveFeignClient;
import reactor.core.publisher.Mono;

@ReactiveFeignClient(name = "entity-processor")
public interface IFeignEntityProcessorService {

    String PRODUCT_PATH = "api/entity/processor/products/internal";
    String TICKET_PATH = "api/entity/processor/tickets/internal";
    String WHATSAPP_INTERNAL_PATH = "api/entity/processor/whatsapp/internal";
    String CALL_INTERNAL_PATH = "api/entity/processor/calls/internal";

    @GetMapping(PRODUCT_PATH + "/{id}")
    Mono<Product> getProductInternal(
            @RequestParam String appCode, @RequestParam String clientCode, @PathVariable("id") BigInteger id);

    @GetMapping(PRODUCT_PATH)
    Mono<List<Product>> getProductsInternal(
            @RequestParam String appCode, @RequestParam String clientCode, List<BigInteger> identity);

    @GetMapping(TICKET_PATH + "/{id}")
    Mono<Ticket> getTicketInternal(
            @RequestParam String appCode, @RequestParam String clientCode, @PathVariable("id") BigInteger id);

    /**
     * Tells entity-processor a WhatsApp message was exchanged, and gets back the deal it belongs to.
     *
     * <p>Call on both directions. Besides answering the question it bumps the conversation ordering
     * on every deal holding that customer's number, which is why it is a POST.
     *
     * @param productId the product the receiving business number is mapped to, or null when that
     *     number is the tenant default and therefore serves every product
     * @param createIfMissing create a deal when the customer has none. True for inbound, since an
     *     unknown number messaging in is a lead and would otherwise be visible to nobody. False for
     *     outbound, where the deal is already known.
     */
    /**
     * Hands a WhatsApp event to entity-processor, which stores it and works out which deal it
     * belongs to.
     *
     * <p>Errors must propagate. The caller holds an outbox row that is only deleted on success, so
     * swallowing a failure here drops the message with nothing left to retry from.
     */
    @PostMapping(WHATSAPP_INTERNAL_PATH + "/inbound")
    Mono<Void> acceptWhatsappInbound(
            @RequestParam String appCode,
            @RequestParam String clientCode,
            @RequestBody WhatsappInboundDispatch dispatch);

    /**
     * Hands a call event to entity-processor, which merges it onto the call it already recorded.
     *
     * <p>Errors must propagate, same as above: the caller holds an outbox row that is only deleted
     * on success, so swallowing a failure here drops the event with nothing left to retry from.
     */
    @PostMapping(CALL_INTERNAL_PATH + "/event")
    Mono<Void> acceptCallEvent(
            @RequestParam String appCode, @RequestParam String clientCode, @RequestBody CallEventDispatch dispatch);

    @PostMapping(TICKET_PATH + "/whatsapp/register")
    Mono<Ticket> registerWhatsappMessage(
            @RequestParam String appCode,
            @RequestParam String clientCode,
            @RequestParam BigInteger productId,
            @RequestParam String phoneNumber,
            @RequestParam String occurredAt,
            @RequestParam boolean createIfMissing);
}
