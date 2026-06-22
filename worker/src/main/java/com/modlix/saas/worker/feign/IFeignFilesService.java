package com.modlix.saas.worker.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;

@FeignClient(name = "files", contextId = "workerFilesService")
public interface IFeignFilesService {

    @PostMapping("/api/files/internal/billing/file-rent-drip")
    Long fileRentDrip();
}
