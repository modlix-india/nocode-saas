package com.fincity.saas.message.service.message.provider.whatsapp;

import com.fincity.saas.message.dao.message.provider.whatsapp.WhatsappMessageDAO;
import com.fincity.saas.message.dto.message.provider.whatsapp.WhatsappPhoneNumber;
import com.fincity.saas.message.model.common.MessageAccess;
import com.fincity.saas.message.model.common.PhoneNumber;
import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class WhatsappCswService {

    private static final int CUSTOMER_SERVICE_WINDOW_HOURS = 24;

    private final WhatsappMessageDAO whatsappMessageDAO;

    @Autowired
    public WhatsappCswService(WhatsappMessageDAO whatsappMessageDAO) {
        this.whatsappMessageDAO = whatsappMessageDAO;
    }

    public Mono<Boolean> isCustomerServiceWindowOpen(
            MessageAccess access, WhatsappPhoneNumber whatsappPhoneNumber, PhoneNumber customerPhone) {

        LocalDateTime windowStart = LocalDateTime.now().minusHours(CUSTOMER_SERVICE_WINDOW_HOURS);

        return whatsappMessageDAO
                .findLastInboundMessageFromCustomer(
                        access,
                        whatsappPhoneNumber.getId(),
                        customerPhone.getNumber(),
                        customerPhone.getCountryCode(),
                        windowStart)
                .hasElement();
    }

    public Mono<Boolean> isFirstMessageToCustomer(
            MessageAccess access, WhatsappPhoneNumber whatsappPhoneNumber, PhoneNumber customerPhone) {

        return whatsappMessageDAO
                .findLastMessageBetweenNumbers(
                        access, whatsappPhoneNumber.getId(), customerPhone.getNumber(), customerPhone.getCountryCode())
                .hasElement()
                .map(hasMessages -> !hasMessages);
    }

    public Mono<Boolean> canSendMessage(
            MessageAccess access,
            WhatsappPhoneNumber whatsappPhoneNumber,
            PhoneNumber customerPhone,
            boolean isTemplateMessage) {

        if (isTemplateMessage) return Mono.just(true);

        return this.isCustomerServiceWindowOpen(access, whatsappPhoneNumber, customerPhone);
    }

    public Mono<CswStatus> getCustomerServiceWindowStatus(
            MessageAccess access, WhatsappPhoneNumber whatsappPhoneNumber, PhoneNumber customerPhone) {

        LocalDateTime windowStart = LocalDateTime.now().minusHours(CUSTOMER_SERVICE_WINDOW_HOURS);

        return this.isFirstMessageToCustomer(access, whatsappPhoneNumber, customerPhone)
                .flatMap(isFirstMessage -> whatsappMessageDAO
                        .findLastInboundMessageFromCustomer(
                                access,
                                whatsappPhoneNumber.getId(),
                                customerPhone.getNumber(),
                                customerPhone.getCountryCode(),
                                windowStart)
                        .map(lastInboundMessage -> {
                            boolean windowOpen = true;
                            LocalDateTime windowExpiresAt = null;

                            if (lastInboundMessage.getCreatedAt() != null)
                                windowExpiresAt =
                                        lastInboundMessage.getCreatedAt().plusHours(CUSTOMER_SERVICE_WINDOW_HOURS);

                            return new CswStatus(
                                    windowOpen, isFirstMessage, windowExpiresAt, lastInboundMessage.getCreatedAt());
                        })
                        .switchIfEmpty(Mono.just(new CswStatus(false, isFirstMessage, null, null))));
    }

    public record CswStatus(
            boolean windowOpen,
            boolean isFirstMessage,
            LocalDateTime windowExpiresAt,
            LocalDateTime lastCustomerMessageAt) {

        public boolean canSendNonTemplateMessage() {
            return windowOpen && !isFirstMessage;
        }

        public boolean canOnlySendTemplateMessage() {
            return !windowOpen || isFirstMessage;
        }
    }
}
