package com.fincity.saas.commons.mongo.configuration;

import org.slf4j.Logger;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.data.mongodb.ReactiveMongoDatabaseFactory;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.convert.DefaultMongoTypeMapper;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.data.mongodb.core.convert.NoOpDbRefResolver;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;

public interface IMongoConfiguration {

    default MappingMongoConverter getMappingMongoConverter(
            ReactiveMongoDatabaseFactory factory, MongoMappingContext context, BeanFactory beanFactory, Logger logger) {
        MappingMongoConverter mappingConverter = new MappingMongoConverter(NoOpDbRefResolver.INSTANCE, context);
        try {
            mappingConverter.setCustomConversions(beanFactory.getBean(MongoCustomConversions.class));
        } catch (NoSuchBeanDefinitionException exception) {
            logger.error("Unable to set converters", exception);
        }
        mappingConverter.setTypeMapper(new DefaultMongoTypeMapper(null));
        mappingConverter.setMapKeyDotReplacement("__d-o-t__");

        return mappingConverter;
    }

    default ReactiveMongoTemplate getReactiveMongoTemplate(
            ReactiveMongoDatabaseFactory factory, MappingMongoConverter convertor) {
        return new ReactiveMongoTemplate(factory, convertor);
    }
}
