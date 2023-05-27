package com.fincity.saas.commons.mq.configuration;

import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.http.converter.xml.MappingJackson2XmlHttpMessageConverter;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;

public abstract interface IMQConfiguration {

	@Bean
	default Jackson2JsonMessageConverter jackson2JsonMessageConverter(ObjectMapper om) {
		return new Jackson2JsonMessageConverter(om);
	}

	default MappingJackson2XmlHttpMessageConverter jackson2XmlHttpMessageConverter(SimpleModule... modules) {
		MappingJackson2XmlHttpMessageConverter jsonConverter = new MappingJackson2XmlHttpMessageConverter();

		XmlMapper xmlMapper = new XmlMapper();
		xmlMapper.setConfig(xmlMapper.getSerializationConfig()
		        .withRootName(""));
		xmlMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		xmlMapper.registerModules(modules);

		jsonConverter.setObjectMapper(xmlMapper);
		return jsonConverter;
	}

}
