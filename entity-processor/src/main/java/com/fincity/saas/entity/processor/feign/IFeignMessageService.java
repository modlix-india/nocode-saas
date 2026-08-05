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
     * Places an outbound call through the provider.
     *
     * <p>Only call after confirming the caller may act on the deal, and pass the number taken from
     * that deal rather than one the caller supplied. The message service performs neither check: it
     * cannot evaluate deal access, and it has no way to tell a legitimate destination from an
     * arbitrary one.
     *
     * <p>Untyped both ways. The response is the message service's own call representation, including
     * the provider's call id and raw payloads, and mirroring that DTO here would create a second copy
     * to keep in step for no gain: this service maps it straight onto its own row.
     */
    @PostMapping(EXOTEL_CALL_PATH + "/internal/make")
    Mono<Map<String, Object>> makeCallInternal(
            @RequestParam String appCode, @RequestParam String clientCode, @RequestBody Map<String, Object> request);

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

    /**
     * Sends a free-form WhatsApp message on a deal.
     *
     * <p>Only call after checking that the caller may act on the deal and that Meta's 24-hour
     * window is open. The message service performs neither check: it cannot evaluate deal access,
     * and since the conversation history moved it no longer holds the timestamps the window is
     * computed from.
     *
     * <p>The caller's token is forwarded explicitly, matching how {@code IFeignSecurityService}
     * does it. Feign here does not propagate the security context on its own, and the message
     * service still attributes the outbound record to the sending user.
     */
    @PostMapping(WHATSAPP_TICKET_PATH + "/internal/send")
    Mono<Map<String, Object>> sendWhatsappMessageByTicket(
            @RequestHeader("Authorization") String authorization,
            @RequestHeader("appCode") String appCode,
            @RequestHeader("clientCode") String clientCode,
            @RequestBody Map<String, Object> request);

    /**
     * Pulls a media file down from Meta and stores it, returning where it landed.
     *
     * <p>Takes Meta's media id rather than a message id: the two services no longer share message
     * rows, so a row id means nothing across the boundary. The caller has already checked that the
     * requester may see the deal.
     */
    @PostMapping(WHATSAPP_PATH + "/internal/media/download")
    Mono<Map<String, Object>> downloadWhatsappMedia(
            @RequestParam String appCode, @RequestParam String clientCode, @RequestBody Map<String, Object> request);

    /** Sends an approved template on a deal. The only thing allowed outside the 24-hour window. */
    @PostMapping(WHATSAPP_TICKET_PATH + "/internal/template/send")
    Mono<Map<String, Object>> sendWhatsappTemplateByTicket(
            @RequestHeader("Authorization") String authorization,
            @RequestHeader("appCode") String appCode,
            @RequestHeader("clientCode") String clientCode,
            @RequestBody Map<String, Object> request);

    /**
     * The tenant's WhatsApp templates.
     *
     * <p>{@code statuses} is one comma-separated value rather than a repeated parameter, which is
     * the encoding the message service's own {@code ConditionUtil} uses for an {@code IN}, so the
     * query it builds is the one {@code ?status=APPROVED&status=PENDING} used to build when the UI
     * called that service directly. Blank means every status.
     *
     * <p>Untyped, for the same reason the conversation reads are: the response is a Spring {@code
     * Page}, which does not deserialize cleanly into {@code PageImpl} over Feign, and mirroring the
     * template DTO here would be a second copy of a large provider-shaped object to keep in step.
     * The body is passed straight through, so the UI sees the shape it already binds to.
     */
    @GetMapping(WHATSAPP_PATH + "/templates/internal")
    Mono<Map<String, Object>> getWhatsappTemplates(
            @RequestParam String appCode,
            @RequestParam String clientCode,
            @RequestParam String statuses,
            @RequestParam String templateName,
            @RequestParam int page,
            @RequestParam int size);

    /**
     * One template, by id or code. The message service resolves it within the tenant named here, so
     * an id belonging to another tenant reads as not found.
     */
    @GetMapping(WHATSAPP_PATH + "/templates/internal/{id}")
    Mono<Map<String, Object>> getWhatsappTemplate(
            @RequestParam String appCode, @RequestParam String clientCode, @PathVariable("id") String idOrCode);

    /** The tenant's WhatsApp business numbers. Untyped for the same reason as the template page. */
    @GetMapping(WHATSAPP_PATH + "/phone-numbers/internal")
    Mono<Map<String, Object>> getWhatsappPhoneNumbers(
            @RequestParam String appCode,
            @RequestParam String clientCode,
            @RequestParam int page,
            @RequestParam int size);

    /**
     * The number a composer preselects. Empty when the tenant has marked no default, which is a
     * working configuration rather than a missing record.
     */
    @GetMapping(WHATSAPP_PATH + "/phone-numbers/internal/default")
    Mono<Map<String, Object>> getDefaultWhatsappPhoneNumber(
            @RequestParam String appCode, @RequestParam String clientCode);
}
