package com.fincity.saas.entity.processor.feign;

import com.fincity.saas.entity.processor.oserver.message.model.ExotelConnectAppletResponse;
import com.fincity.saas.entity.processor.oserver.message.model.IncomingCallRequest;
import com.fincity.saas.entity.processor.oserver.message.model.WhatsappTemplateSendRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import reactivefeign.spring.config.ReactiveFeignClient;
import reactor.core.publisher.Mono;

@ReactiveFeignClient(name = "message")
public interface IFeignMessageService {

    String MESSAGE_PATH = "/api/message";
    String EXOTEL_CALL_PATH = MESSAGE_PATH + "/call/exotel";
    String WHATSAPP_TICKET_PATH = MESSAGE_PATH + "/whatsapp/ticket";

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
}
