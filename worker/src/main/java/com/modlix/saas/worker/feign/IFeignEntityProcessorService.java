package com.modlix.saas.worker.feign;

import java.util.Map;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "entity-processor", contextId = "workerEntityProcessorService")
public interface IFeignEntityProcessorService {

    @PostMapping("/api/entity/processor/partners/internal/denorm")
    Map<String, Object> triggerPartnerDenormalization(@RequestParam("delta") boolean delta);
}
