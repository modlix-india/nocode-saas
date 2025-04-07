package com.fincity.saas.commons.core.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincity.saas.commons.core.service.CoreMessageResourceService;
import com.fincity.saas.commons.jooq.configuration.AbstractJooqBaseInProgramConfig;
import com.fincity.saas.commons.mq.configuration.IMQConfiguration;
import com.fincity.saas.commons.security.ISecurityConfiguration;
import com.fincity.saas.commons.util.LogUtil;
import org.springframework.amqp.rabbit.annotation.RabbitListenerConfigurer;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistrar;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.annotation.Bean;
import org.springframework.data.mongodb.ReactiveMongoDatabaseFactory;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.convert.DefaultMongoTypeMapper;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.data.mongodb.core.convert.NoOpDbRefResolver;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import reactivefeign.client.ReactiveHttpRequestInterceptor;
import reactor.core.publisher.Mono;

import java.util.List;

public abstract class AbstractCoreConfiguration extends AbstractJooqBaseInProgramConfig
        implements ISecurityConfiguration, IMQConfiguration, RabbitListenerConfigurer {

    protected CoreMessageResourceService messageService;

    protected AbstractCoreConfiguration(
            ObjectMapper objectMapper, String schema, CoreMessageResourceService messageService) {
        super(objectMapper, schema);
        this.messageService = messageService;
    }

    @Bean
    public ReactiveHttpRequestInterceptor feignInterceptor() {
        return request -> Mono.deferContextual(ctxView -> {
            if (ctxView.hasKey(LogUtil.DEBUG_KEY))
                request.headers().put(LogUtil.DEBUG_KEY, List.of(ctxView.get(LogUtil.DEBUG_KEY)));

            return Mono.just(request);
        });
    }

    @Bean
    MappingMongoConverter mappingConverter(
            ReactiveMongoDatabaseFactory factory, MongoMappingContext context, BeanFactory beanFactory) {
        MappingMongoConverter mappingConverter = new MappingMongoConverter(NoOpDbRefResolver.INSTANCE, context);
        try {
            mappingConverter.setCustomConversions(beanFactory.getBean(MongoCustomConversions.class));
        } catch (NoSuchBeanDefinitionException ignore) {
            logger.error("Unable to set converters", ignore);
        }
        mappingConverter.setTypeMapper(new DefaultMongoTypeMapper(null));
        mappingConverter.setMapKeyDotReplacement("__d-o-t__");

        return mappingConverter;
    }

    @Bean
    ReactiveMongoTemplate reactiveMongoTemplate(ReactiveMongoDatabaseFactory factory, MappingMongoConverter convertor) {
        return new ReactiveMongoTemplate(factory, convertor);
    }

    @Override
    public void configureRabbitListeners(RabbitListenerEndpointRegistrar registrar) {
        // This is a method in interface which requires an implementation in a concrete
        // class
        // that is why it is left empty.
    }
}
