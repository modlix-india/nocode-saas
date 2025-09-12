package com.fincity.saas.message.controller.message.provider.whatsapp;

import com.fincity.saas.message.controller.base.BaseUpdatableController;
import com.fincity.saas.message.dao.message.provider.whatsapp.WhatsappMessageDAO;
import com.fincity.saas.message.dto.message.Message;
import com.fincity.saas.message.dto.message.provider.whatsapp.WhatsappMessage;
import com.fincity.saas.message.jooq.tables.records.MessageWhatsappMessagesRecord;
import com.fincity.saas.message.model.common.Identity;
import com.fincity.saas.message.model.common.PhoneNumber;
import com.fincity.saas.message.model.message.whatsapp.response.Response;
import com.fincity.saas.message.model.request.message.provider.whatsapp.WhatsappMediaRequest;
import com.fincity.saas.message.model.request.message.provider.whatsapp.WhatsappMessageCswRequest;
import com.fincity.saas.message.model.request.message.provider.whatsapp.WhatsappMessageRequest;
import com.fincity.saas.message.model.request.message.provider.whatsapp.WhatsappReadRequest;
import com.fincity.saas.message.service.message.provider.whatsapp.WhatsappCswService;
import com.fincity.saas.message.service.message.provider.whatsapp.WhatsappMessageService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
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
}
