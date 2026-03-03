package com.fincity.sass.worker.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "core")
public interface IFeignCoreService {

    String PATH = "/api/core/function/execute/{namespace}/{name}";
    String PATH_VARIABLE_NAMESPACE = "namespace";
    String PATH_VARIABLE_NAME = "name";

    @PostMapping(PATH)
    String executeWith(
            @RequestHeader("X-Forwarded-Host") String forwardedHost,
            @RequestHeader("X-Forwarded-Port") String forwardedPort,
            @RequestHeader String appCode,
            @RequestHeader String clientCode,
            @PathVariable(PATH_VARIABLE_NAMESPACE) String namespace,
            @PathVariable(PATH_VARIABLE_NAME) String name,
            @RequestBody String jsonString);
}
