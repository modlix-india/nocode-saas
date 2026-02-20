package com.fincity.sass.worker.feign;

import com.fincity.sass.worker.dto.ExpireTicketsResult;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "entity-processor")
public interface IFeignEntityProcessor {

    String TICKETS_EXPIRATION_RUN_PATH = "/api/entity/processor/tickets/expiration/run";

    @PostMapping(TICKETS_EXPIRATION_RUN_PATH)
    ExpireTicketsResult runTicketExpiration(
            @RequestHeader("appCode") String appCode,
            @RequestHeader("clientCode") String clientCode);
}
