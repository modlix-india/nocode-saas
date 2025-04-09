package com.fincity.saas.commons.configuration;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fincity.nocode.kirun.engine.json.schema.array.ArraySchemaType;
import com.fincity.nocode.kirun.engine.json.schema.array.ArraySchemaType.ArraySchemaTypeAdapter;
import com.fincity.nocode.kirun.engine.json.schema.object.AdditionalType;
import com.fincity.nocode.kirun.engine.json.schema.object.AdditionalType.AdditionalTypeAdapter;
import com.fincity.nocode.kirun.engine.json.schema.type.Type;
import com.fincity.nocode.kirun.engine.json.schema.type.Type.SchemaTypeAdapter;
import com.fincity.saas.commons.codec.RedisJSONCodec;
import com.fincity.saas.commons.codec.RedisObjectCodec;
import com.fincity.saas.commons.gson.LocalDateTimeAdapter;
import com.fincity.saas.commons.jackson.CommonsSerializationModule;
import com.fincity.saas.commons.jackson.SortSerializationModule;
import com.fincity.saas.commons.jackson.TupleSerializationModule;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import io.lettuce.core.pubsub.api.async.RedisPubSubAsyncCommands;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
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

public abstract class AbstractBaseConfiguration implements WebFluxConfigurer {

    protected static final Logger logger = LoggerFactory.getLogger(AbstractBaseConfiguration.class);

    protected ObjectMapper objectMapper;

    @Value("${redis.url:}")
    private String redisURL;

    @Value("${redis.codec:object}")
    private String codecType;

    private RedisCodec<String, Object> objectCodec;

    protected AbstractBaseConfiguration(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    protected void initialize() {
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        this.objectMapper.setDefaultPropertyInclusion(JsonInclude.Value.construct(Include.NON_NULL, Include.ALWAYS));
        this.objectMapper.setDefaultPropertyInclusion(JsonInclude.Value.construct(Include.NON_EMPTY, Include.ALWAYS));
        this.objectMapper.registerModule(new CommonsSerializationModule());
        this.objectMapper.registerModule(new TupleSerializationModule());
        this.objectMapper.registerModule(new SortSerializationModule());

        this.objectCodec = "object".equals(codecType) ? new RedisObjectCodec() : new RedisJSONCodec(this.objectMapper);
    }

    @Bean
    public Gson makeGson() {
        ArraySchemaTypeAdapter arraySchemaTypeAdapter = new ArraySchemaTypeAdapter();

        AdditionalTypeAdapter additionalTypeAdapter = new AdditionalTypeAdapter();

        Gson gson = new GsonBuilder()
                .registerTypeAdapter(Type.class, new SchemaTypeAdapter())
                .registerTypeAdapter(AdditionalType.class, additionalTypeAdapter)
                .registerTypeAdapter(ArraySchemaType.class, arraySchemaTypeAdapter)
                .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
                .create();

        arraySchemaTypeAdapter.setGson(gson);
        additionalTypeAdapter.setGson(gson);
        return gson;
    }

    @Override
    public void configureHttpMessageCodecs(ServerCodecConfigurer configurer) {

        configurer.defaultCodecs().jackson2JsonDecoder(new Jackson2JsonDecoder(this.objectMapper));
        configurer.defaultCodecs().jackson2JsonEncoder(new Jackson2JsonEncoder(this.objectMapper));
        configurer.defaultCodecs().maxInMemorySize(this.getInMemorySize());
        WebFluxConfigurer.super.configureHttpMessageCodecs(configurer);
    }

    protected int getInMemorySize() {
        return 1024 * 1024 * 50;
    }

    @Override
    public void configureArgumentResolvers(ArgumentResolverConfigurer configurer) {
        configurer.addCustomResolver(new ReactivePageableHandlerMethodArgumentResolver());
    }

    @Bean
    public PasswordEncoder passwordEncoder() throws NoSuchAlgorithmException {
        return new BCryptPasswordEncoder(10, SecureRandom.getInstanceStrong());
    }

    @Bean
    public RedisClient redisClient() {
        if (redisURL == null || redisURL.isBlank()) return null;

        return RedisClient.create(redisURL);
    }

    @Bean
    public RedisAsyncCommands<String, Object> asyncCommands(@Autowired(required = false) RedisClient client) {

        if (client == null) return null;

        StatefulRedisConnection<String, Object> connection = client.connect(objectCodec);
        return connection.async();
    }

    @Bean
    public StatefulRedisPubSubConnection<String, String> subConnection(
            @Autowired(required = false) RedisClient client) {

        if (client == null) return null;

        return client.connectPubSub();
    }

    @Bean
    public RedisPubSubAsyncCommands<String, String> subRedisAsyncCommand(
            @Autowired(required = false) StatefulRedisPubSubConnection<String, String> connection) {

        if (connection == null) return null;

        return connection.async();
    }

    @Bean
    public RedisPubSubAsyncCommands<String, String> pubRedisAsyncCommand(
            @Autowired(required = false) RedisClient client) {

        if (client == null) return null;

        return client.connectPubSub().async();
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {

        registry.addMapping("/**")
                .allowedOriginPatterns(
                        "https://*.modlix.com",
                        "https://*.dev.modlix.com",
                        "https://*.stage.modlix.com",
                        "https://modlix.com",
                        "https://dev.modlix.com",
                        "https://stage.modlix.com",
                        "http://localhost:1234",
                        "http://localhost:3000",
                        "http://localhost:8080")
                .allowedMethods("*")
                .maxAge(3600);
    }

    @Bean
    public Caffeine<Object, Object> caffeineConfig() {
        return Caffeine.newBuilder().expireAfterAccess(Duration.ofMinutes(5));
    }

    @Bean
    public CacheManager cacheManager(Caffeine<Object, Object> caffeine) {
        CaffeineCacheManager caffeineCacheManager = new CaffeineCacheManager();
        caffeineCacheManager.setCaffeine(caffeine);
        return caffeineCacheManager;
    }
}
