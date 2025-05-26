package com.fincity.saas.entity.processor.feign;

import java.util.List;
import org.jooq.types.ULong;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import reactivefeign.spring.config.ReactiveFeignClient;
import reactor.core.publisher.Mono;

@ReactiveFeignClient(name = "security", path = "/api/security/users")
public interface UserServiceClient {

    @PostMapping("/internal/getProfileUsers/{appCode}")
    Mono<List<ULong>> getProfileUsers(@PathVariable String appCode, @RequestBody List<ULong> profileIds);
}
