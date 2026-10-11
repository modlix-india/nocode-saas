package com.fincity.saas.message.service.dispatch.handler;

import com.fincity.saas.commons.mq.events.EventCreationService;
import com.fincity.saas.message.enums.dispatch.DispatchChannel;
import com.fincity.saas.message.enums.dispatch.DispatchEventType;
import com.fincity.saas.message.service.event.WhatsappEventService;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * WhatsApp for numbers owned by core, raised as {@code WHATSAPP_INBOUND}.
 *
 * <p>Every WhatsApp dispatch kind rides this one event, told apart by {@code eventType}: {@code
 * INBOUND_MESSAGE}, {@code MESSAGE_STATUS}, {@code MEDIA_READY}, {@code PROFILE_PICTURE} and {@code
 * MESSAGE_EDIT}. One EventAction per tenant then, not five that have to be kept in step.
 *
 * <p>Also nudges the tenant's open pages once the event is out, which is the live update. The nudge
 * goes when core <i>receives</i> the event, not when its function has finished writing, so a page
 * that re-reads immediately can still miss the row: it should re-read after a short delay.
 */
@Component
public class CoreWhatsappHandler extends CoreEventHandler {

    public static final String CORE_EVENT_NAME = "WHATSAPP_INBOUND";

    private static final Logger logger = LoggerFactory.getLogger(CoreWhatsappHandler.class);

    private final WhatsappEventService whatsappEventService;

    public CoreWhatsappHandler(EventCreationService eventCreationService, WhatsappEventService whatsappEventService) {
        super(eventCreationService);
        this.whatsappEventService = whatsappEventService;
    }

    @Override
    public DispatchChannel getChannel() {
        return DispatchChannel.WHATSAPP;
    }

    @Override
    public String getCoreEventName() {
        return CORE_EVENT_NAME;
    }

    @Override
    protected String getDefaultEventType() {
        return DispatchEventType.INBOUND_MESSAGE.name();
    }

    /** The receiving session's row id, as the entity-processor dispatch already carries it. */
    @Override
    protected Object getNumberId(Map<String, Object> payload) {
        return payload.get("whatsappPhoneNumberId");
    }

    @Override
    protected Mono<Void> afterDelivery(String appCode, String clientCode, Map<String, Object> data) {
        return this.whatsappEventService
                .sendConversationChanged(appCode, clientCode, data)
                .onErrorResume(e -> {
                    logger.warn("Could not nudge pages of {}/{} about a WhatsApp change.", appCode, clientCode, e);
                    return Mono.empty();
                });
    }
}
