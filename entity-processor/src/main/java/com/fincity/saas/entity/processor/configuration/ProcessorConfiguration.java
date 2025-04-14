package com.fincity.saas.entity.processor.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.jooq.configuration.AbstractJooqBaseConfiguration;
import com.fincity.saas.commons.jooq.jackson.UnsignedNumbersSerializationModule;
import com.fincity.saas.commons.mongo.jackson.KIRuntimeSerializationModule;
import com.fincity.saas.commons.mq.configuration.IMQConfiguration;
import com.fincity.saas.commons.security.ISecurityConfiguration;
import com.fincity.saas.commons.security.service.FeignAuthenticationService;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.entity.processor.service.ProcessorMessageResourceService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListenerConfigurer;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistrar;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import reactivefeign.client.ReactiveHttpRequestInterceptor;
import reactor.core.publisher.Mono;

import java.util.List;

@Configuration
public class ProcessorConfiguration extends AbstractJooqBaseConfiguration
        implements ISecurityConfiguration, IMQConfiguration, RabbitListenerConfigurer {

    protected ProcessorMessageResourceService messageService;

    protected ProcessorConfiguration(ObjectMapper objectMapper) {
        super(objectMapper);
    }

    @Override
    @PostConstruct
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
    SecurityWebFilterChain filterChain(ServerHttpSecurity http, FeignAuthenticationService authService) {
        return this.springSecurityFilterChain(http, authService, this.objectMapper);
    }

    @Bean
    ReactiveHttpRequestInterceptor feignInterceptor() {
        return request -> Mono.deferContextual(ctxView -> {
            if (ctxView.hasKey(LogUtil.DEBUG_KEY))
                request.headers().put(LogUtil.DEBUG_KEY, List.of(ctxView.get(LogUtil.DEBUG_KEY)));
            return Mono.just(request);
        });
    }

    @Override
    public void configureRabbitListeners(RabbitListenerEndpointRegistrar registrar) {}
}
