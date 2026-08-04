package com.fincity.saas.message.controller.message.provider.whatsapp;

import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.model.Query;
import com.fincity.saas.message.controller.base.BaseUpdatableController;
import com.fincity.saas.message.dao.message.provider.whatsapp.WhatsappMessageDAO;
import com.fincity.saas.message.dto.message.Message;
import com.fincity.saas.message.dto.message.provider.whatsapp.WhatsappMessage;
import com.fincity.saas.message.jooq.tables.records.MessageWhatsappMessagesRecord;
import com.fincity.saas.message.model.common.Identity;
import com.fincity.saas.message.model.common.PhoneNumber;
import com.fincity.saas.message.oserver.files.model.FileDetail;
import com.fincity.saas.message.model.message.whatsapp.response.Response;
import com.fincity.saas.message.model.request.message.provider.whatsapp.WhatsappMediaByIdRequest;
import com.fincity.saas.message.model.request.message.provider.whatsapp.WhatsappMediaRequest;
import com.fincity.saas.message.model.request.message.provider.whatsapp.WhatsappMessageCswRequest;
import com.fincity.saas.message.model.request.message.provider.whatsapp.WhatsappMessageRequest;
import com.fincity.saas.message.model.request.message.provider.whatsapp.WhatsappReadRequest;
import com.fincity.saas.message.service.message.provider.whatsapp.WhatsappCswService;
import com.fincity.saas.message.service.message.provider.whatsapp.WhatsappMessageService;
import java.util.Map;
import org.jooq.types.ULong;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/message/whatsapp")
public class WhatsappMessageController
        extends BaseUpdatableController<
                MessageWhatsappMessagesRecord, WhatsappMessage, WhatsappMessageDAO, WhatsappMessageService> {

    @PostMapping("/send")
    public Mono<ResponseEntity<Message>> sendWhatsappMessage(@RequestBody WhatsappMessageRequest request) {
        return this.service.sendMessage(request).map(ResponseEntity::ok);
    }

    @GetMapping("/send/csw")
    public Mono<ResponseEntity<WhatsappCswService.CswStatus>> getCswStatus(
            @RequestParam String connectionName,
            @RequestParam Identity whatsappPhoneNumberId,
            @RequestParam PhoneNumber customerNumber) {
        return this.service
                .getCswStatus(WhatsappMessageCswRequest.of(connectionName, whatsappPhoneNumberId, customerNumber))
                .map(ResponseEntity::ok);
    }

    @PostMapping("/read")
    public Mono<ResponseEntity<Response>> markMessageAsRead(@RequestBody WhatsappReadRequest request) {
        return this.service.markMessageAsRead(request).map(ResponseEntity::ok);
    }

    @PostMapping("/media/download")
    public Mono<ResponseEntity<WhatsappMessage>> downloadMediaFile(@RequestBody WhatsappMediaRequest request) {
        return this.service.downloadMediaFile(request).map(ResponseEntity::ok);
    }

    /**
     * Fetches a media file from Meta and stores it, for a caller whose messages live elsewhere.
     *
     * <p>Takes Meta's media id, not a row id here, because the two services no longer share message
     * rows. entity-processor confirms the requester may see the deal, pulls the media id out of its
     * own copy of the payload, and calls this.
     */
    @PostMapping("/internal/media/download")
    public Mono<ResponseEntity<FileDetail>> downloadMediaByMediaIdInternal(
            @RequestParam("appCode") String appCode,
            @RequestParam("clientCode") String clientCode,
            @RequestBody WhatsappMediaByIdRequest request) {
        return this.service
                .downloadMediaByMediaIdInternal(
                        appCode,
                        clientCode,
                        request.getConnectionName(),
                        request.getMediaId(),
                        request.getFileLocation())
                .map(ResponseEntity::ok);
    }

    /**
     * A deal's thread, for entity-processor only. Behind {@code /internal} because this service
     * cannot evaluate deal access; entity-processor checks that the caller may see the ticket and
     * then calls this.
     *
     * <p>Superseded: entity-processor now stores and reads its own messages. Kept only until the
     * transitional dual-write is removed and this service's message table is dropped.
     */
    @GetMapping("/internal/ticket/{ticketId}/messages")
    public Mono<ResponseEntity<Page<WhatsappMessage>>> readByTicketInternal(
            @RequestParam("appCode") String appCode,
            @RequestParam("clientCode") String clientCode,
            @PathVariable("ticketId") ULong ticketId,
            Pageable pageable) {
        return this.service
                .readByTicketInternal(
                        appCode,
                        clientCode,
                        ticketId,
                        pageable == null ? PageRequest.of(0, 20, Sort.Direction.DESC, "id") : pageable)
                .map(ResponseEntity::ok);
    }

    // ---------------------------------------------------------------------------------------
    // Generic listing is disabled for WhatsApp messages.
    //
    // The inherited endpoints below let any authenticated user in the tenant enumerate any
    // customer's entire thread by phone number, with no check that they can see the underlying
    // deal. Reads must go through entity-processor, which owns deal access. These overrides keep
    // the inherited routes from being served rather than silently leaving them open.
    // ---------------------------------------------------------------------------------------

    @Override
    @GetMapping()
    public Mono<ResponseEntity<Page<WhatsappMessage>>> readPageFilter(
            Pageable pageable, ServerHttpRequest request) {
        return listingDisabled();
    }

    @Override
    @PostMapping(PATH_QUERY)
    public Mono<ResponseEntity<Page<WhatsappMessage>>> readPageFilter(@RequestBody Query query) {
        return listingDisabled();
    }

    @Override
    @GetMapping(EAGER_BASE)
    public Mono<ResponseEntity<Page<Map<String, Object>>>> readPageFilterEager(
            Pageable pageable, ServerHttpRequest request) {
        return listingDisabled();
    }

    @Override
    @PostMapping(EAGER_PATH_QUERY)
    public Mono<ResponseEntity<Page<Map<String, Object>>>> readPageFilterEager(
            @RequestBody Query query, ServerHttpRequest request) {
        return listingDisabled();
    }

    private <T> Mono<ResponseEntity<T>> listingDisabled() {
        return Mono.error(new GenericException(
                HttpStatus.FORBIDDEN,
                "Listing WhatsApp messages directly is not permitted. Read a deal's thread through"
                        + " entity-processor, which checks that you can see the deal."));
    }
}
