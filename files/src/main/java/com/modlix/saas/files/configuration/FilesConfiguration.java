package com.modlix.saas.files.configuration;

import java.net.URI;
import java.time.Duration;

import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.modlix.saas.commons2.jooq.configuration.AbstractJooqBaseConfiguration;
import com.modlix.saas.commons2.security.ISecurityConfiguration;
import com.modlix.saas.commons2.security.service.IAuthenticationService;
import com.modlix.saas.commons2.security.util.LogUtil;
import com.modlix.saas.files.service.FilesMessageResourceService;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import jakarta.annotation.PostConstruct;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
public class FilesConfiguration extends AbstractJooqBaseConfiguration implements ISecurityConfiguration {

    @Value("${files.resources.endpoint}")
    private String endpoint;

    @Value("${files.resources.accessKeyId}")
    private String accessKeyId;

    @Value("${files.resources.secretAccessKey}")
    private String secretAccessKey;

    private final FilesMessageResourceService messageService;

    public FilesConfiguration(FilesMessageResourceService messageService, ObjectMapper objectMapper) {
        super(objectMapper);
        this.messageService = messageService;
    }

    @PostConstruct
    @Override
    public void initialize() {
        super.initialize(messageService);
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, IAuthenticationService authService, ObjectMapper om)
            throws Exception {
        return this.springSecurityFilterChain(http, authService, om);
    }

    @Bean
    RequestInterceptor feignInterceptor() {
        return new RequestInterceptor() {
            @Override
            public void apply(RequestTemplate template) {
                // Get debug code from MDC and add it to the request headers
                String debugCode = MDC.get(LogUtil.DEBUG_KEY);
                if (debugCode != null) {
                    template.header(LogUtil.DEBUG_KEY, debugCode);
                }
            }
        };
    }

    @Bean
    S3Client s3Client() {

        SdkHttpClient httpClient = ApacheHttpClient.builder()
                // POOL / QUEUE
                .maxConnections(128) // raise if host has headroom; 64→128 is a safe bump
                .connectionAcquisitionTimeout(Duration.ofSeconds(30))

                // TIMEOUTS: quick to connect, generous to transfer
                .connectionTimeout(Duration.ofSeconds(5))
                .socketTimeout(Duration.ofSeconds(120))
                .build();

        return S3Client.builder()
                .endpointOverride(URI.create(endpoint)) // e.g. https://<accountid>.r2.cloudflarestorage.com
                .region(software.amazon.awssdk.regions.Region.of("auto")) // R2 requires "auto"
                .credentialsProvider(
                        StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKeyId, secretAccessKey)))
                .serviceConfiguration(c -> c
                        // keep true if using the "universal" endpoint above (bucket in PATH)
                        .pathStyleAccessEnabled(true)
                        // optional, avoids extra validations
                        .checksumValidationEnabled(false))
                .httpClient(httpClient)
                // Whole-call deadlines: prefer attempt timeout to fail faster per try
                .overrideConfiguration(o -> o
                        .apiCallAttemptTimeout(Duration.ofSeconds(60)) // per retry attempt
                        .apiCallTimeout(Duration.ofMinutes(5)) // whole call upper bound
                )
                .build();
    }
}
