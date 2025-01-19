package com.fincity.saas.ui.service;

import java.util.function.Function;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.model.ObjectWithUniqueID;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.StringUtil;

import jakarta.annotation.PostConstruct;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Service
public class IndexHTMLCacheService {

    @Value("${ui.htmlCacheUrl:}")
    private String htmlCacheUrl;

    @Value("${ui.htmlCacheEnv}")
    private String htmlCacheEnvironment;

    @Value("${ui.waitTime:8000}")
    private String waitTime;

    private WebClient htmlCacheClient;

    @PostConstruct
    public void init() {
        if (StringUtil.safeIsBlank(this.htmlCacheUrl))
            return;
        this.htmlCacheClient = WebClient.builder()
                .baseUrl(this.htmlCacheUrl)
                .codecs(c -> c.defaultCodecs().maxInMemorySize(50 * 1024 * 1024)).build();
    }

    public boolean dontHaveCache() {
        return this.htmlCacheClient == null;
    }

    public Mono<ObjectWithUniqueID<String>> get(String fullURL, String appCode, String clientCode, String device) {

        if (this.dontHaveCache())
            return Mono.just(new ObjectWithUniqueID<>(""));

        return FlatMapUtil.flatMapMono(

                () -> this.htmlCacheClient.get().uri(uriBuilder -> uriBuilder.path("/" + fullURL)
                        .queryParam("appCode", appCode)
                        .queryParam("clientCode", clientCode)
                        .queryParam("device", device)
                        .queryParam("env", this.htmlCacheEnvironment)
                        .queryParam("waitTime", this.waitTime).build())
                        .retrieve().bodyToMono(String.class)
                        .onErrorReturn(""),

                resString -> StringUtil.safeIsBlank(resString) ? Mono.empty()
                        : Mono.just(new ObjectWithUniqueID<>(resString)))

                .contextWrite(Context.of(LogUtil.METHOD_NAME, "IndexHTMLCacheService.get"));
    }

    public <D> Function<D, Mono<D>> evictFunction(String appCode) {

        return v -> FlatMapUtil.flatMapMono(

                () -> this.evict(appCode),

                e -> Mono.just(v)

        ).contextWrite(Context.of(LogUtil.METHOD_NAME, "IndexHTMLCacheService.evict"));
    }

    public Mono<String> evict(String appCode) {

        if (this.dontHaveCache())
            return Mono.just("");

        return this.htmlCacheClient.delete().uri(uriBuilder -> uriBuilder.path("/all")
                .queryParam("appCode", appCode)
                .queryParam("env", this.htmlCacheEnvironment).build()).retrieve().bodyToMono(String.class)
                .onErrorReturn("");
    }
}
