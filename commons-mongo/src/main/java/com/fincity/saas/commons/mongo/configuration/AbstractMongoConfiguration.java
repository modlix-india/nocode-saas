package com.fincity.saas.commons.mongo.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincity.saas.commons.configuration.AbstractBaseConfiguration;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.data.mongodb.ReactiveMongoDatabaseFactory;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;

public abstract class AbstractMongoConfiguration extends AbstractBaseConfiguration implements IMongoConfiguration {

    protected AbstractMongoConfiguration(ObjectMapper objectMapper) {
        super(objectMapper);
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
}
