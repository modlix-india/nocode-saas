package com.fincity.saas.ui.repository;

import com.fincity.saas.ui.document.MobileAppGenerationStatus;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;

public interface MobileAppGenerationStatusRepository extends ReactiveCrudRepository<MobileAppGenerationStatus, String> {
    Flux<MobileAppGenerationStatus> findByAppCodeAndClientCodeAndMobileAppKeyIsIn(String appCode, String clientCode, Collection<String> mobileAppKeys);

    Mono<MobileAppGenerationStatus> findFirstByStatusOrderByUpdatedAtAsc(MobileAppGenerationStatus.Status status);
}
