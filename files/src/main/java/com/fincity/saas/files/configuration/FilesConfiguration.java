package com.fincity.saas.files.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.jooq.configuration.AbstractJooqBaseConfiguration;
import com.fincity.saas.commons.security.ISecurityConfiguration;
import com.fincity.saas.commons.security.service.FeignAuthenticationService;
import com.fincity.saas.commons.util.LogUtil;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.util.matcher.OrServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.PathPatternParserServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher;
import reactivefeign.client.ReactiveHttpRequestInterceptor;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.async.SdkAsyncHttpClient;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;

import java.net.URI;
import java.time.Duration;
import java.util.List;

@Configuration
public class FilesConfiguration extends AbstractJooqBaseConfiguration
    implements ISecurityConfiguration {

    @Value("${files.resources.endpoint}")
    private String endpoint;

    @Value("${files.resources.accessKeyId}")
    private String accessKeyId;

    @Value("${files.resources.secretAccessKey}")
    private String secretAccessKey;

    public FilesConfiguration(ObjectMapper objectMapper) {
        super(objectMapper);
    }

    @Override
    @PostConstruct
    public void initialize() {
        super.initialize();
        Logger log = LoggerFactory.getLogger(FlatMapUtil.class);
        FlatMapUtil.setLogConsumer(signal -> LogUtil.logIfDebugKey(signal, (name, v) -> {

            if (name != null)
                log.debug("{} - {}", name, v);
            else
                log.debug(v);
        }));
    }

    @Bean
    public SecurityWebFilterChain filterChain(ServerHttpSecurity http,
                                              FeignAuthenticationService authService) {
        ServerWebExchangeMatcher matcher = new OrServerWebExchangeMatcher(
            new PathPatternParserServerWebExchangeMatcher("/api/files/static/file/**"),
            new PathPatternParserServerWebExchangeMatcher("/api/files/secured/file/**"),
            new PathPatternParserServerWebExchangeMatcher(
                "/api/files/secured/downloadFileByKey/*"));

        return this.springSecurityFilterChain(http, authService, this.objectMapper, matcher,

            "/api/files/static/file/**", "/api/files/internal/**", "/api/files/secured/downloadFileByKey/*");
    }

    @Bean
    public ReactiveHttpRequestInterceptor feignInterceptor() {
        return request -> Mono.deferContextual(ctxView -> {

            if (ctxView.hasKey(LogUtil.DEBUG_KEY)) {
                String key = ctxView.get(LogUtil.DEBUG_KEY);

                request.headers().put(LogUtil.DEBUG_KEY, List.of(key));
            }

            return Mono.just(request);
        });
    }

    @Bean
    public S3AsyncClient s3Client() {

        final SdkAsyncHttpClient httpClient = NettyNioAsyncHttpClient.builder()
            .readTimeout(Duration.ofMinutes(20))
            .writeTimeout(Duration.ofMinutes(20))
            .connectionTimeout(Duration.ofMinutes(20))
            .maxConcurrency(64)
            .build();

        return S3AsyncClient.builder()
            .region(Region.US_EAST_1)
            .endpointOverride(URI.create(endpoint))
            .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKeyId, secretAccessKey)))
            .httpClient(httpClient)
            .overrideConfiguration(o -> o.apiCallTimeout(java.time.Duration.ofMinutes(20)))
            .build();
    }

    @Bean
    public DataBufferFactory dataBufferFactory() {
        return new DefaultDataBufferFactory(true, 64 * 1024); // 64 KB chunks
    }

    @Override
    protected int getInMemorySize() {
        return 500 * 1024 * 1024;
    }
}
