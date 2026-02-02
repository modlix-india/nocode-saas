package com.fincity.saas.ui.repository;

import com.fincity.saas.ui.document.MobileApp;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;

public interface MobileAppRepository extends ReactiveCrudRepository<MobileApp, String> {

    Flux<MobileApp> findByAppCodeAndClientCode(String appCode, String clientCode);

    Mono<MobileApp> findFirstByStatusOrderByUpdatedAtAsc(MobileApp.Status status);
}
