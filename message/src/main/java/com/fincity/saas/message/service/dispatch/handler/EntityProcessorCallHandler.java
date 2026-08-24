package com.fincity.saas.message.service.dispatch.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincity.saas.message.enums.dispatch.DispatchChannel;
import com.fincity.saas.message.feign.IFeignEntityProcessorService;
import com.fincity.saas.message.model.request.dispatch.CallEventDispatch;
import com.fincity.saas.message.service.dispatch.IDispatchHandler;
import java.util.Map;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * The CRM consumer for calls, and today the only one.
 *
 * <p>Calls whose {@code OWNER_SERVICE} is {@code entity-processor} route here. Unlike WhatsApp, the
 * owner is recorded on the call rather than on a number, because Exotel numbers are configured per
 * product in the CRM and this service holds no table of them.
 */
@Component
public class EntityProcessorCallHandler implements IDispatchHandler {

    public static final String SERVICE_NAME = "entity-processor";

    private final IFeignEntityProcessorService entityProcessorService;
    private final ObjectMapper objectMapper;

    public EntityProcessorCallHandler(IFeignEntityProcessorService entityProcessorService, ObjectMapper objectMapper) {
        this.entityProcessorService = entityProcessorService;
        this.objectMapper = objectMapper;
    }

    @Override
    public String getServiceName() {
        return SERVICE_NAME;
    }

    @Override
    public DispatchChannel getChannel() {
        return DispatchChannel.CALL;
    }

    @Override
    public Mono<Void> handle(String appCode, String clientCode, Map<String, Object> payload) {
        // No error handling on purpose. A failure has to reach the dispatcher so the outbox row
        // survives and is retried.
        return this.entityProcessorService.acceptCallEvent(
                appCode, clientCode, this.objectMapper.convertValue(payload, CallEventDispatch.class));
    }
}
