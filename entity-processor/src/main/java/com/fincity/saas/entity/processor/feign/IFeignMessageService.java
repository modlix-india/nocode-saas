package com.fincity.saas.entity.processor.feign;

import com.fincity.saas.entity.processor.oserver.message.model.ExotelConnectAppletResponse;
import com.fincity.saas.entity.processor.oserver.message.model.IncomingCallRequest;
import com.fincity.saas.entity.processor.oserver.message.model.WhatsappTemplateSendRequest;
import java.math.BigInteger;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import reactivefeign.spring.config.ReactiveFeignClient;
import reactor.core.publisher.Mono;

@ReactiveFeignClient(name = "message")
public interface IFeignMessageService {

    String MESSAGE_PATH = "/api/message";
    String EXOTEL_CALL_PATH = MESSAGE_PATH + "/call/exotel";
    String WHATSAPP_PATH = MESSAGE_PATH + "/whatsapp";
    String WHATSAPP_TICKET_PATH = WHATSAPP_PATH + "/ticket";

    @PostMapping(EXOTEL_CALL_PATH + "/connect")
    Mono<ExotelConnectAppletResponse> connectCall(
            @RequestHeader("appCode") String appCode,
            @RequestHeader("clientCode") String clientCode,
            @RequestBody IncomingCallRequest callRequest);

    /**
     * Sends a stored WhatsApp template to a ticket. The message service resolves the template by id
     * and fills its body placeholders from {@code variables}, so the caller does not need to know
     * the template's component structure.
     */
    @PostMapping(WHATSAPP_TICKET_PATH + "/template/send-from-queue")
    Mono<Void> sendWhatsappTemplateFromQueue(
            @RequestHeader("appCode") String appCode,
            @RequestHeader("clientCode") String clientCode,
            @RequestBody WhatsappTemplateSendRequest request);

    /**
     * A deal's WhatsApp thread. Only call this after confirming the caller may see the ticket; the
     * message service performs no deal-level check of its own.
     *
     * <p>Untyped on purpose. The response is a Spring {@code Page}, which does not deserialize
     * cleanly into {@code PageImpl} over Feign, and mirroring the full WhatsApp message DTO here
     * would create a second copy to keep in step. The body is passed straight through to the
     * caller, so the UI sees exactly the shape it saw before.
     */
    @GetMapping(WHATSAPP_PATH + "/internal/ticket/{ticketId}/messages")
    Mono<Map<String, Object>> getTicketWhatsappMessages(
            @RequestParam String appCode,
            @RequestParam String clientCode,
            @PathVariable("ticketId") BigInteger ticketId,
            @RequestParam int page,
            @RequestParam int size);
}
