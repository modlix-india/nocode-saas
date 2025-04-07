package com.fincity.saas.commons.core.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincity.saas.commons.jooq.configuration.AbstractJooqBaseInProgramConfig;
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

public abstract class AbstractJooqMongoConfig extends AbstractJooqBaseInProgramConfig {

  protected AbstractJooqMongoConfig(ObjectMapper objectMapper, String schema) {
    super(objectMapper, schema);
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
}
