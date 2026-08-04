package com.fincity.saas.message.service.message.provider.whatsapp.dispatch;

import com.fincity.saas.message.feign.IFeignEntityProcessorService;
import com.fincity.saas.message.model.request.message.provider.whatsapp.WhatsappInboundDispatch;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * The CRM consumer, and today the only one.
 *
 * <p>Numbers whose {@code OWNER_SERVICE} is {@code entity-processor} route here.
 */
@Component
public class EntityProcessorInboundHandler implements IWhatsappInboundHandler {

    public static final String SERVICE_NAME = "entity-processor";

    private final IFeignEntityProcessorService entityProcessorService;

    public EntityProcessorInboundHandler(IFeignEntityProcessorService entityProcessorService) {
        this.entityProcessorService = entityProcessorService;
    }

    @Override
    public String getServiceName() {
        return SERVICE_NAME;
    }

    @Override
    public Mono<Void> handle(String appCode, String clientCode, WhatsappInboundDispatch dispatch) {
        // No error handling on purpose. A failure has to reach the dispatcher so the outbox row
        // survives and is retried.
        return this.entityProcessorService.acceptWhatsappInbound(appCode, clientCode, dispatch);
    }
}
