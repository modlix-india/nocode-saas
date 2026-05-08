package com.fincity.saas.entity.processor.oserver.core.service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.model.dto.AbstractOverridableDTO;
import com.fincity.saas.commons.security.feign.IFeignSecurityService;
import com.fincity.saas.entity.processor.feign.IFeignCoreService;
import com.fincity.saas.entity.processor.service.ProcessorMessageResourceService;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.publisher.Mono;

public abstract class AbstractCoreService<T extends AbstractOverridableDTO<T>> {

    protected ProcessorMessageResourceService msgService;

    protected IFeignCoreService coreService;

    protected IFeignSecurityService securityService;

    protected abstract String getObjectName();

    @Autowired
    private void setMsgService(ProcessorMessageResourceService msgService) {
        this.msgService = msgService;
    }

    @Autowired
    private void setCoreService(IFeignCoreService coreService) {
        this.coreService = coreService;
    }

    @Autowired
    private void setSecurityService(IFeignSecurityService securityService) {
        this.securityService = securityService;
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

        return this.fetchCoreDocument(appCode, urlClientCode, clientCode, documentName);
    }
}
