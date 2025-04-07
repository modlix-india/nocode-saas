package com.fincity.saas.commons.core.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.core.service.CoreMessageResourceService;
import com.fincity.saas.commons.jooq.jackson.UnsignedNumbersSerializationModule;
import com.fincity.saas.commons.mongo.jackson.KIRuntimeSerializationModule;
import com.fincity.saas.commons.mq.configuration.IMQConfiguration;
import com.fincity.saas.commons.security.ISecurityConfiguration;
import com.fincity.saas.commons.util.LogUtil;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListenerConfigurer;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistrar;
import org.springframework.context.annotation.Bean;
import reactivefeign.client.ReactiveHttpRequestInterceptor;
import reactor.core.publisher.Mono;

import java.util.List;

public abstract class AbstractCoreConfiguration extends AbstractJooqMongoConfig
        implements ISecurityConfiguration, IMQConfiguration, RabbitListenerConfigurer {

    protected CoreMessageResourceService messageService;

    protected AbstractCoreConfiguration(
            ObjectMapper objectMapper, String schema, CoreMessageResourceService messageService) {
        super(objectMapper, schema);
        this.messageService = messageService;
    }

    @PostConstruct
    @Override
    public void initialize() {
        super.initialize();
        this.objectMapper.registerModule(new KIRuntimeSerializationModule());
        this.objectMapper.registerModule(new UnsignedNumbersSerializationModule(messageService));
        Logger log = LoggerFactory.getLogger(FlatMapUtil.class);
        FlatMapUtil.setLogConsumer(signal -> LogUtil.logIfDebugKey(signal, (name, v) -> {
            if (name != null) log.debug("{} - {}", name, v);
            else log.debug(v);
        }));
    }

    @Bean
    public ReactiveHttpRequestInterceptor feignInterceptor() {
        return request -> Mono.deferContextual(ctxView -> {
            if (ctxView.hasKey(LogUtil.DEBUG_KEY)) {
                String key = ctxView.get(LogUtil.DEBUG_KEY);

                request.headers().put(LogUtil.DEBUG_KEY, List.of(key));
            }

            return Mono.just(request);
        });
    }

    @Override
    public void configureRabbitListeners(RabbitListenerEndpointRegistrar registrar) {
        // This is a method in interface which requires an implementation in a concrete
        // class
        // that is why it is left empty.
    }
}
