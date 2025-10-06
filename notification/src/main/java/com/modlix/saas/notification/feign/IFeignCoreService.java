package com.modlix.saas.notification.feign;

import org.springframework.cloud.openfeign.FeignClient;

@FeignClient(name = "core")
public interface IFeignCoreService {

    
}
