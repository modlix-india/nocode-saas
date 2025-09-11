package com.fincity.saas.message.service.message;

import com.fincity.saas.message.dao.message.MessageWebhookDAO;
import com.fincity.saas.message.dto.message.MessageWebhook;
import com.fincity.saas.message.enums.MessageSeries;
import com.fincity.saas.message.jooq.tables.records.MessageMessageWebhooksRecord;
import com.fincity.saas.message.model.common.MessageAccess;
import com.fincity.saas.message.model.message.whatsapp.webhook.IWebHookEvent;
import com.fincity.saas.message.model.response.MessageResponse;
import com.fincity.saas.message.service.base.BaseUpdatableService;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class MessageWebhookService
        extends BaseUpdatableService<MessageMessageWebhooksRecord, MessageWebhook, MessageWebhookDAO> {

    private static final String MESSAGE_WEBHOOK_CACHE = "messageWebhook";

    @Override
    protected String getCacheName() {
        return MESSAGE_WEBHOOK_CACHE;
    }

    @Override
    public MessageSeries getMessageSeries() {
        return MessageSeries.MESSAGE_WEBHOOKS;
    }

    @Override
    protected Mono<MessageWebhook> updatableEntity(MessageWebhook entity) {
        return super.updatableEntity(entity).flatMap(existing -> {
            existing.setProcessed(entity.isProcessed());
            return Mono.just(existing);
        });
    }

    public Mono<MessageResponse> processed(MessageWebhook messageWebhook) {
        return super.update(messageWebhook.setProcessed(Boolean.TRUE))
                .map(response -> MessageResponse.ofSuccess(response.getCode(), this.getMessageSeries()));
    }

    public Mono<MessageWebhook> createWhatsappWebhookEvent(MessageAccess access, IWebHookEvent event) {
        return this.createInternal(
                access, new MessageWebhook().setEvent(event.toMap()).setProvider("whatsapp"));
    }
}
