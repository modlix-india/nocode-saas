package com.fincity.saas.commons.mongo.configuration;

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

import com.fincity.saas.commons.configuration.AbstractBaseConfiguration;

public class AbstractMongoConfiguration extends AbstractBaseConfiguration {

	@Bean
	MappingMongoConverter mappingMongoConverter(ReactiveMongoDatabaseFactory factory, MongoMappingContext context,
	        BeanFactory beanFactory) {
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
	ReactiveMongoTemplate mongoTemplate(ReactiveMongoDatabaseFactory factory, MappingMongoConverter convertor) {
		return new ReactiveMongoTemplate(factory, convertor);
	}

}
