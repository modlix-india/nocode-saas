package com.fincity.saas.message.service.dispatch.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincity.saas.message.enums.dispatch.DispatchChannel;
import com.fincity.saas.message.feign.IFeignEntityProcessorService;
import com.fincity.saas.message.model.request.message.provider.whatsapp.WhatsappInboundDispatch;
import com.fincity.saas.message.service.dispatch.IDispatchHandler;
import java.util.Map;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * The CRM consumer for WhatsApp, and today the only one.
 *
 * <p>Numbers whose {@code OWNER_SERVICE} is {@code entity-processor} route here.
 *
 * <p>The payload arrives as a map because the outbox stores it that way and one dispatcher serves
 * every channel. Converting it back to the typed dispatch here is what keeps the cross-service
 * contract compile-checked at the boundary that owns it.
 */
@Component
public class EntityProcessorWhatsappHandler implements IDispatchHandler {

    public static final String SERVICE_NAME = "entity-processor";

    private final IFeignEntityProcessorService entityProcessorService;
    private final ObjectMapper objectMapper;

    public EntityProcessorWhatsappHandler(
            IFeignEntityProcessorService entityProcessorService, ObjectMapper objectMapper) {
        this.entityProcessorService = entityProcessorService;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getServiceName() {
        return SERVICE_NAME;
    }

    @Override
    public DispatchChannel getChannel() {
        return DispatchChannel.WHATSAPP;
    }

    @Override
    public Mono<Void> handle(String appCode, String clientCode, Map<String, Object> payload) {
        // No error handling on purpose. A failure has to reach the dispatcher so the outbox row
        // survives and is retried.
        return this.entityProcessorService.acceptWhatsappInbound(
                appCode, clientCode, this.objectMapper.convertValue(payload, WhatsappInboundDispatch.class));
    }
}
