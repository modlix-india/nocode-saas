package com.fincity.saas.commons.configuration;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.data.web.ReactivePageableHandlerMethodArgumentResolver;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.reactive.config.CorsRegistry;
import org.springframework.web.reactive.config.WebFluxConfigurer;
import org.springframework.web.reactive.result.method.annotation.ArgumentResolverConfigurer;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fincity.saas.commons.codec.RedisJSONCodec;
import com.fincity.saas.commons.codec.RedisObjectCodec;
import com.fincity.saas.commons.jackson.CommonsSerializationModule;
import com.fincity.saas.commons.jackson.SortSerializationModule;
import com.fincity.saas.commons.jackson.TupleSerializationModule;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import io.lettuce.core.pubsub.api.async.RedisPubSubAsyncCommands;

public abstract class AbstractBaseConfiguration implements WebFluxConfigurer {

	protected static final Logger logger = LoggerFactory.getLogger(AbstractBaseConfiguration.class);

	@Autowired
	protected ObjectMapper objectMapper;

	@Value("${redis.url:}")
	private String redisURL;

	@Value("${redis.codec:object}")
	private String codecType;

	private RedisCodec<String, Object> objectCodec;

	protected void initialize() {
		this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
		this.objectMapper.setDefaultPropertyInclusion(JsonInclude.Value.construct(Include.NON_NULL, Include.ALWAYS));
		this.objectMapper.setDefaultPropertyInclusion(JsonInclude.Value.construct(Include.NON_EMPTY, Include.ALWAYS));
		this.objectMapper.registerModule(new CommonsSerializationModule());
		this.objectMapper.registerModule(new TupleSerializationModule());
		this.objectMapper.registerModule(new SortSerializationModule());

		this.objectCodec = "object".equals(codecType) ? new RedisObjectCodec() : new RedisJSONCodec(this.objectMapper);
	}

	@Override
	public void configureHttpMessageCodecs(ServerCodecConfigurer configurer) {

		configurer.defaultCodecs()
				.jackson2JsonDecoder(new Jackson2JsonDecoder(this.objectMapper));
		configurer.defaultCodecs()
				.jackson2JsonEncoder(new Jackson2JsonEncoder(this.objectMapper));
		configurer.defaultCodecs()
				.maxInMemorySize(50 * 1024 * 1024);
		WebFluxConfigurer.super.configureHttpMessageCodecs(configurer);
	}

	@Override
	public void configureArgumentResolvers(ArgumentResolverConfigurer configurer) {
		configurer.addCustomResolver(new ReactivePageableHandlerMethodArgumentResolver());
	}

	@Bean
	PasswordEncoder passwordEncoder() throws NoSuchAlgorithmException {
		return new BCryptPasswordEncoder(10, SecureRandom.getInstanceStrong());
	}

	@Bean
	RedisClient redisClient() {
		if (redisURL == null || redisURL.isBlank())
			return null;

		return RedisClient.create(redisURL);
	}

	@Bean
	RedisAsyncCommands<String, Object> asyncCommands(@Autowired(required = false) RedisClient client) {

		if (client == null)
			return null;

		StatefulRedisConnection<String, Object> connection = client.connect(objectCodec);
		return connection.async();
	}

	@Bean
	StatefulRedisPubSubConnection<String, String> subConnection(@Autowired(required = false) RedisClient client) {

		if (client == null)
			return null;

		return client.connectPubSub();
	}

	@Bean
	RedisPubSubAsyncCommands<String, String> subRedisAsyncCommand(
			@Autowired(required = false) StatefulRedisPubSubConnection<String, String> connection) {

		if (connection == null)
			return null;

		return connection.async();
	}

	@Bean
	RedisPubSubAsyncCommands<String, String> pubRedisAsyncCommand(@Autowired(required = false) RedisClient client) {

		if (client == null)
			return null;

		return client.connectPubSub()
				.async();
	}

	@Override
	public void addCorsMappings(CorsRegistry registry) {

		registry.addMapping("/**")
				.allowedOriginPatterns("https://*.openbracket.in", "https://*.dev.openbracket.in",
						"https://*.stage.openbracket.in", "https://openbracket.in", "https://dev.openbracket.in",
						"https://stage.openbracket.in", "http://localhost:1234", "http://localhost:3000",
						"http://localhost:8080")
				.allowedMethods("*")
				.maxAge(3600);
	}
}
