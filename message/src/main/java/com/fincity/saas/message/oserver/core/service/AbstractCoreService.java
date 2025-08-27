package com.fincity.saas.message.oserver.core.service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.model.dto.AbstractOverridableDTO;
import com.fincity.saas.commons.security.feign.IFeignSecurityService;
import com.fincity.saas.commons.service.CacheService;
import com.fincity.saas.message.feign.IFeignCoreService;
import com.fincity.saas.message.service.MessageResourceService;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public abstract class AbstractCoreService<T extends AbstractOverridableDTO<T>> {

    protected static final String CACHE_NAME = "Cache";

    protected MessageResourceService msgService;

    protected CacheService cacheService;

    protected IFeignCoreService coreService;

    protected IFeignSecurityService securityService;

    protected abstract String getObjectName();

    @Autowired
    private void setMsgService(MessageResourceService msgService) {
        this.msgService = msgService;
    }

    @Autowired
    private void setCacheService(CacheService cacheService) {
        this.cacheService = cacheService;
    }

    @Autowired
    private void setCoreService(IFeignCoreService coreService) {
        this.coreService = coreService;
    }

    @Autowired
    private void setSecurityService(IFeignSecurityService securityService) {
        this.securityService = securityService;
    }

    protected String getCacheName(String appCode, String name) {
        return this.getObjectName() + CACHE_NAME + "_" + appCode + "_" + name;
    }

    protected abstract Mono<T> fetchCoreDocument(
            String appCode, String urlClientCode, String clientCode, String documentName);

    public Mono<T> getCoreDocument(String appCode, String clientCode, String documentName) {
        return this.getCoreDocument(appCode, clientCode, clientCode, documentName);
    }

    public Mono<T> getCoreDocument(String appCode, String urlClientCode, String clientCode, String documentName) {
        return FlatMapUtil.flatMapMono(
                () -> this.securityService.appInheritance(appCode, urlClientCode, clientCode),
                inheritance -> this.getDocument(appCode, urlClientCode, clientCode, inheritance, documentName));
    }

    private Mono<T> getDocument(
            String appCode, String urlClientCode, String clientCode, List<String> inheritance, String documentName) {

        if (inheritance == null || inheritance.isEmpty()) return Mono.empty();

        if (inheritance.size() == 1)
            return this.cacheService
                    .<T>get(this.getCacheName(appCode, documentName), inheritance.getFirst())
                    .switchIfEmpty(this.getCoreDocumentInternal(appCode, urlClientCode, clientCode, documentName));

        return Flux.fromIterable(inheritance)
                .flatMap(cc -> this.cacheService.<T>get(this.getCacheName(appCode, documentName), cc))
                .next()
                .switchIfEmpty(this.getCoreDocumentInternal(appCode, urlClientCode, clientCode, documentName));
    }

    private Mono<T> getCoreDocumentInternal(
            String appCode, String urlClientCode, String clientCode, String documentName) {
        return FlatMapUtil.flatMapMono(
                () -> this.fetchCoreDocument(appCode, urlClientCode, clientCode, documentName),
                document -> this.cacheService.put(this.getCacheName(appCode, documentName), document, clientCode));
    }
}
