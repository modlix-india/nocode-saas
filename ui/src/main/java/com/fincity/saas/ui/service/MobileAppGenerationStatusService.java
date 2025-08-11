package com.fincity.saas.ui.service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.exeception.GenericException;
import com.fincity.saas.commons.util.LogUtil;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.saas.ui.document.MobileAppGenerationStatus;
import com.fincity.saas.ui.model.MobileAppStatusUpdateRequest;
import com.fincity.saas.ui.repository.MobileAppGenerationStatusRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@Service
public class MobileAppGenerationStatusService {

    private final MobileAppGenerationStatusRepository repo;

    private final UIMessageResourceService messageResourceService;

    public MobileAppGenerationStatusService(MobileAppGenerationStatusRepository repo, UIMessageResourceService messageResourceService) {
        this.repo = repo;
        this.messageResourceService = messageResourceService;
    }

    public Mono<MobileAppGenerationStatus> updateStatus(BigInteger userId, String appCode, String clientCode, String mobileAppKey, MobileAppGenerationStatus.Status status) {
        return FlatMapUtil.flatMapMono(

                () -> this.repo.findByAppCodeAndClientCodeAndMobileAppKeyIsIn(appCode, clientCode, List.of(mobileAppKey)).collectList(),

                list -> {
                    LocalDateTime now = LocalDateTime.now();

                    if (list.isEmpty()) {

                        MobileAppGenerationStatus obj = new MobileAppGenerationStatus();
                        obj.setCreatedAt(now).setCreatedBy(userId.toString());
                        obj.setUpdatedAt(now).setUpdatedBy(userId.toString());

                        return this.repo.save(obj
                                .setAppCode(appCode)
                                .setClientCode(clientCode)
                                .setMobileAppKey(mobileAppKey)
                                .setVersion(1)
                                .setStatus(status)

                        );
                    }

                    if (list.size() != 1) {
                        return this.messageResourceService.throwMessage(msg -> new GenericException(HttpStatus.INTERNAL_SERVER_ERROR, msg)
                                , UIMessageResourceService.MULTIPLE_GEN_STATUS, appCode, clientCode, mobileAppKey);
                    }

                    MobileAppGenerationStatus existing = list.getFirst();

                    if (existing.getStatus() == status) {
                        return Mono.just(existing);
                    }

                    existing.setUpdatedAt(now).setUpdatedBy(userId.toString());
                    if (status == MobileAppGenerationStatus.Status.PENDING)
                        existing.setVersion(existing.getVersion() + 1);

                    existing.setStatus(status);

                    return this.repo.save(existing);
                }
        ).contextWrite(Context.of(LogUtil.METHOD_NAME, "MobileAppGenerationStatusService.updateStatus"));
    }

    public Mono<Map<String, MobileAppGenerationStatus.Status>> getMobileAppGenerationStatus(String appCode, String clientCode, Collection<String> mobileAppKeys) {

        return this.repo.findByAppCodeAndClientCodeAndMobileAppKeyIsIn(appCode, clientCode, mobileAppKeys)
                .collectMap(MobileAppGenerationStatus::getMobileAppKey, MobileAppGenerationStatus::getStatus);
    }

    public Mono<MobileAppGenerationStatus> getNextMobileAppToGenerate() {

        return this.repo.findFirstByStatusOrderByUpdatedAtAsc(MobileAppGenerationStatus.Status.PENDING);
    }

    public Mono<Boolean> updateStatus(String id, MobileAppStatusUpdateRequest request) {

        return this.repo.findById(id)
                .flatMap(e -> {
                    e.setUpdatedAt(LocalDateTime.now());
                    if (!StringUtil.safeIsBlank(request.getErrorMessage())) {
                        e.setStatus(MobileAppGenerationStatus.Status.FAILED);
                        e.setErrorMessage(request.getErrorMessage());
                    } else {
                        e.setStatus(request.getStatus());
                    }
                    return this.repo.save(e);
                })
                .map(e -> true);
    }
}
