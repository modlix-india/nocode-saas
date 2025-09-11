package com.fincity.saas.ui.service;

import com.fincity.saas.commons.util.StringUtil;
import com.fincity.saas.ui.document.MobileApp;
import com.fincity.saas.ui.model.MobileAppStatusUpdateRequest;
import com.fincity.saas.ui.repository.MobileAppRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class MobileAppService {

    private final MobileAppRepository repo;

    public MobileAppService(MobileAppRepository repo) {
        this.repo = repo;
    }

    public Mono<List<MobileApp>> list(String appCode, String clientCode) {
        return this.repo.findByAppCodeAndClientCode(appCode, clientCode).collectList();
    }

    public Mono<MobileApp> update(MobileApp mobileApp) {
        if (mobileApp.getId() == null)
            return this.repo.save(mobileApp);

        return this.repo.findById(mobileApp.getId())
                .flatMap(existing -> {
                    mobileApp.getDetails().setVersion(mobileApp.getId() == null ? 0 : existing.getDetails().getVersion() + 1);
                    return this.repo.save(mobileApp);
                });
    }

    public Mono<MobileApp> getNextMobileAppToGenerate() {

        return this.repo.findFirstByStatusOrderByUpdatedAtAsc(MobileApp.Status.PENDING);
    }

    public Mono<Boolean> updateStatus(String id, MobileAppStatusUpdateRequest request) {

        return this.repo.findById(id)
                .flatMap(e -> {
                    e.setUpdatedAt(LocalDateTime.now());
                    if (!StringUtil.safeIsBlank(request.getErrorMessage())) {
                        e.setStatus(MobileApp.Status.FAILED);
                        e.setErrorMessage(request.getErrorMessage());
                    } else {
                        e.setStatus(request.getStatus());
                        if (request.getStatus() == MobileApp.Status.SUCCESS) {
                            e.setErrorMessage(null);
                        }
                    }
                    e.setAndroidAppURL(request.getAndroidAppURL());
                    e.setIosAppURL(request.getIosAppURL());
                    return this.repo.save(e);
                })
                .map(e -> true);
    }

    public Mono<MobileApp> readMobileApp(String id) {
        return this.repo.findById(id);
    }

    public Mono<Boolean> deleteMobileApp(String id) {
        return this.repo.deleteById(id).thenReturn(true);
    }
}
