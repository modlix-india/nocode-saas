package com.modlix.saas.commons2.mongo.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.modlix.saas.commons2.configuration.AbstractBaseConfiguration;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;

public abstract class AbstractMongoConfiguration extends AbstractBaseConfiguration implements IMongoConfiguration {

    protected AbstractMongoConfiguration(ObjectMapper objectMapper) {
        super(objectMapper);
    }

    @Bean
    MappingMongoConverter mappingConverter(
            MongoDatabaseFactory factory, MongoMappingContext context, BeanFactory beanFactory) {
        return this.getMappingMongoConverter(factory, context, beanFactory, logger);
    }

    @Bean
    MongoTemplate mongoTemplate(MongoDatabaseFactory factory, MappingMongoConverter convertor) {
        return this.getMongoTemplate(factory, convertor);
    }
}

