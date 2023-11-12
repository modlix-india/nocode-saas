package com.fincity.saas.multi.fiegn;

import reactivefeign.spring.config.ReactiveFeignClient;

@ReactiveFeignClient(name = "core")
public interface IFeignCoreService {

}
