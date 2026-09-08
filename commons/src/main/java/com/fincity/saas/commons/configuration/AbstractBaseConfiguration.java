package com.fincity.saas.commons.configuration;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalDate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
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
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.SerializableString;
import com.fasterxml.jackson.core.util.JsonGeneratorDelegate;
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
import com.fincity.saas.commons.gson.LocalDateAdapter;
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

public abstract class AbstractBaseConfiguration implements WebFluxConfigurer {

    protected static final Logger logger = LoggerFactory.getLogger(AbstractBaseConfiguration.class);

    protected ObjectMapper objectMapper;

    @Value("${redis.url:}")
    private String redisURL;

    @Value("${redis.codec:object}")
    private String codecType;

    @Value("${cache.local.max-weight-bytes:67108864}")
    private long localCacheMaxWeightBytes;

    @Value("${cache.local.expire-after-write-minutes:60}")
    private long localCacheExpireAfterWriteMinutes;

    @Value("${cache.local.max-instances:400}")
    private long localCacheMaxInstances;

    @Value("${cache.local.instance-idle-minutes:240}")
    private long localCacheInstanceIdleMinutes;

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
                .registerTypeAdapter(LocalDate.class, new LocalDateAdapter())
                .create();

        arraySchemaTypeAdapter.setGson(gson);
        additionalTypeAdapter.setGson(gson);
        return gson;
    }

    @Override
    public void configureHttpMessageCodecs(ServerCodecConfigurer configurer) {

        configurer.defaultCodecs()
            .jackson2JsonDecoder(new Jackson2JsonDecoder(this.objectMapper));
        configurer.defaultCodecs()
            .jackson2JsonEncoder(new Jackson2JsonEncoder(this.objectMapper));
        configurer.defaultCodecs()
            .maxInMemorySize(this.getInMemorySize());
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
        if (redisURL == null || redisURL.isBlank())
            return null;

        return RedisClient.create(redisURL);
    }

    @Bean
    public RedisAsyncCommands<String, Object> asyncCommands(@Autowired(required = false) RedisClient client) {

        if (client == null)
            return null;

        StatefulRedisConnection<String, Object> connection = client.connect(objectCodec);
        return connection.async();
    }

    @Bean
    public StatefulRedisPubSubConnection<String, String> subConnection(
        @Autowired(required = false) RedisClient client) {

        if (client == null)
            return null;

        return client.connectPubSub();
    }

    @Bean
    public RedisPubSubAsyncCommands<String, String> subRedisAsyncCommand(
        @Autowired(required = false) StatefulRedisPubSubConnection<String, String> connection) {

        if (connection == null)
            return null;

        return connection.async();
    }

    @Bean
    public RedisPubSubAsyncCommands<String, String> pubRedisAsyncCommand(
        @Autowired(required = false) RedisClient client) {

        if (client == null)
            return null;

        return client.connectPubSub()
            .async();
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {

        registry.addMapping("/**")
            .allowedOriginPatterns("https://*.modlix.com", "https://*.dev.modlix.com",
                "https://*.stage.modlix.com", "https://modlix.com", "https://dev.modlix.com",
                "https://stage.modlix.com", "http://localhost:1234", "http://localhost:3000",
                "http://localhost:8080")
            .allowedMethods("*")
            .maxAge(3600);
    }

    @Bean
    public Caffeine<Object, Object> caffeineConfig() {
        // Bound the local (L1) cache by approximate memory (cache.local.max-weight-bytes): entries
        // are large, highly variable definition objects, so a flat maximumSize is a poor proxy.
        // W-TinyLFU evicts only under weight pressure, so entries persist while there is room.
        // No idle (expireAfterAccess) expiry — it evicted entries even when caches were nearly empty,
        // killing hit rate. A long expireAfterWrite (cache.local.expire-after-write-minutes, 0 = off)
        // is kept ONLY as a staleness backstop should a cross-instance invalidation be missed;
        // correctness normally comes from explicit evictAll-on-write.
        Caffeine<Object, Object> builder = Caffeine.newBuilder()
            .maximumWeight(this.localCacheMaxWeightBytes)
            .weigher(this::weighCacheEntry)
            .recordStats();
        if (this.localCacheExpireAfterWriteMinutes > 0)
            builder = builder.expireAfterWrite(Duration.ofMinutes(this.localCacheExpireAfterWriteMinutes));
        return builder;
    }

    @Bean
    public CacheManager cacheManager(Caffeine<Object, Object> caffeine) {
        return new BoundedCaffeineCacheManager(caffeine, this.localCacheMaxInstances,
                Duration.ofMinutes(this.localCacheInstanceIdleMinutes));
    }

    /**
     * Approximate retained heap of a cache entry, in bytes.
     *
     * This used to count the entry's serialised JSON bytes, which badly
     * under-counts: cached definitions are deeply nested LinkedHashMap trees with
     * short String keys, and every one of those tiny JSON tokens costs tens of
     * bytes of object header, hash table slot and String on the heap. Measured
     * against a real 106 KB page definition from dev, the serialised length was
     * 0.18x its actual retained heap — so a nominally 64 MiB cap was really
     * letting a single cache instance hold several hundred MB, and could not
     * bound a 2 GB heap.
     *
     * The payload is walked through a JsonGenerator so the estimate reacts to the
     * SHAPE of the value, not just its length: a single long string weighs about
     * what it costs, while a deep map of small keys is priced per node. On that
     * same page this model lands at 1.45x the measured retained heap — deliberately
     * a little conservative, since over-estimating costs some hit rate while
     * under-estimating costs the whole service. Constants are 64-bit HotSpot with
     * compressed oops and are approximate; this bounds a cache, it does not need
     * to be exact.
     */
    private int weighCacheEntry(Object key, Object value) {
        long weight = key instanceof String s ? HEAP_STRING_BASE + s.length() : HEAP_SCALAR;
        try (HeapWeighingGenerator counter = new HeapWeighingGenerator(
                this.objectMapper.getFactory().createGenerator(DISCARDING_STREAM))) {
            this.objectMapper.writer().without(SerializationFeature.INDENT_OUTPUT).writeValue(counter, value);
            weight += counter.weight;
        } catch (Exception e) {
            weight += HEAP_UNWEIGHABLE; // nominal weight when a value cannot be serialised
        }
        return (int) Math.min(weight, Integer.MAX_VALUE);
    }

    /** LinkedHashMap: object header and fields (~48) plus its smallest Node[] table (~80). */
    private static final int HEAP_MAP_BASE = 128;

    /** ArrayList: object header and fields (~40) plus its backing Object[] header (~24). */
    private static final int HEAP_LIST_BASE = 64;

    /** LinkedHashMap$Entry (~40) plus the table slot that points at it. */
    private static final int HEAP_ENTRY = 48;

    /** String object (~24) plus its byte[] header (~16); callers add the character count. */
    private static final int HEAP_STRING_BASE = 40;

    /** A boxed number, boolean, or null reference. */
    private static final int HEAP_SCALAR = 16;

    /** Charged when a value cannot be serialised at all, so it is never free. */
    private static final int HEAP_UNWEIGHABLE = 4096;

    private static final OutputStream DISCARDING_STREAM = new OutputStream() {
        @Override
        public void write(int b) {
            // Weight comes from the generator callbacks; the bytes themselves are not needed.
        }

        @Override
        public void write(byte[] b, int off, int len) {
            // As above.
        }
    };

    /**
     * Counts the approximate heap cost of the value being serialised through it.
     *
     * Every write is still delegated so serialisation behaves exactly as it
     * normally would (context validation included); the bytes land in
     * {@link #DISCARDING_STREAM}. Only the structural callbacks are intercepted.
     */
    private static final class HeapWeighingGenerator extends JsonGeneratorDelegate {

        private long weight;

        private HeapWeighingGenerator(JsonGenerator delegate) {
            super(delegate);
        }

        @Override
        public void writeStartObject() throws IOException {
            this.weight += HEAP_MAP_BASE;
            super.writeStartObject();
        }

        @Override
        public void writeStartObject(Object forValue) throws IOException {
            this.weight += HEAP_MAP_BASE;
            super.writeStartObject(forValue);
        }

        @Override
        public void writeStartArray() throws IOException {
            this.weight += HEAP_LIST_BASE;
            super.writeStartArray();
        }

        @Override
        public void writeStartArray(Object forValue) throws IOException {
            this.weight += HEAP_LIST_BASE;
            super.writeStartArray(forValue);
        }

        @Override
        public void writeFieldName(String name) throws IOException {
            this.weight += HEAP_ENTRY + HEAP_STRING_BASE + name.length();
            super.writeFieldName(name);
        }

        @Override
        public void writeFieldName(SerializableString name) throws IOException {
            this.weight += HEAP_ENTRY + HEAP_STRING_BASE + name.getValue().length();
            super.writeFieldName(name);
        }

        @Override
        public void writeString(String text) throws IOException {
            this.weight += HEAP_STRING_BASE + (text == null ? 0 : text.length());
            super.writeString(text);
        }

        @Override
        public void writeString(char[] text, int offset, int len) throws IOException {
            this.weight += HEAP_STRING_BASE + len;
            super.writeString(text, offset, len);
        }

        @Override
        public void writeNumber(int v) throws IOException {
            this.weight += HEAP_SCALAR;
            super.writeNumber(v);
        }

        @Override
        public void writeNumber(long v) throws IOException {
            this.weight += HEAP_SCALAR;
            super.writeNumber(v);
        }

        @Override
        public void writeNumber(double v) throws IOException {
            this.weight += HEAP_SCALAR;
            super.writeNumber(v);
        }

        @Override
        public void writeNumber(float v) throws IOException {
            this.weight += HEAP_SCALAR;
            super.writeNumber(v);
        }

        @Override
        public void writeNumber(BigDecimal v) throws IOException {
            this.weight += HEAP_SCALAR;
            super.writeNumber(v);
        }

        @Override
        public void writeNumber(BigInteger v) throws IOException {
            this.weight += HEAP_SCALAR;
            super.writeNumber(v);
        }

        @Override
        public void writeBoolean(boolean state) throws IOException {
            this.weight += HEAP_SCALAR;
            super.writeBoolean(state);
        }

        @Override
        public void writeNull() throws IOException {
            this.weight += HEAP_SCALAR;
            super.writeNull();
        }
    }
}
