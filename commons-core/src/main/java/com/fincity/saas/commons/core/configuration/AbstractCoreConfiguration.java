package com.fincity.saas.commons.core.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincity.saas.commons.jooq.configuration.AbstractJooqBaseConfiguration;
import com.fincity.saas.commons.mongo.configuration.IMongoConfiguration;
import com.fincity.saas.commons.mq.configuration.IMQConfiguration;
import com.fincity.saas.commons.security.ISecurityConfiguration;
import com.fincity.saas.commons.util.LogUtil;
import java.util.List;
import org.springframework.amqp.rabbit.annotation.RabbitListenerConfigurer;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistrar;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.data.mongodb.ReactiveMongoDatabaseFactory;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import reactivefeign.client.ReactiveHttpRequestInterceptor;
import reactor.core.publisher.Mono;

public abstract class AbstractCoreConfiguration extends AbstractJooqBaseConfiguration
        implements ISecurityConfiguration, IMQConfiguration, RabbitListenerConfigurer, IMongoConfiguration {

    protected AbstractCoreConfiguration(ObjectMapper objectMapper) {
        super(objectMapper);
    }

    @Bean
    ReactiveHttpRequestInterceptor feignInterceptor() {

        return request -> Mono.deferContextual(ctxView -> {
            if (ctxView.hasKey(LogUtil.DEBUG_KEY)) {
                String value = ctxView.get(LogUtil.DEBUG_KEY);
                request.headers().put(LogUtil.DEBUG_KEY, List.of(value));
            }

            // The draft surface marker travels every internal hop, so a nested
            // service call from a draft request also reads the draft surface.
            if (Boolean.TRUE.equals(ctxView.getOrDefault(LogUtil.DRAFT_KEY, Boolean.FALSE)))
                request.headers().put(LogUtil.DRAFT_KEY, List.of("true"));

            return Mono.just(request);
        });
    }

    @Bean
    MappingMongoConverter mappingConverter(
            ReactiveMongoDatabaseFactory factory, MongoMappingContext context, BeanFactory beanFactory) {
        return this.getMappingMongoConverter(factory, context, beanFactory, logger);
    }

    @Bean
    ReactiveMongoTemplate reactiveMongoTemplate(ReactiveMongoDatabaseFactory factory, MappingMongoConverter convertor) {
        return this.getReactiveMongoTemplate(factory, convertor);
    }

    @Override
    public void configureRabbitListeners(RabbitListenerEndpointRegistrar registrar) {
        // This is a method in interface that requires an implementation in a concrete
        //  class, that is why it is left empty.
    }
}
