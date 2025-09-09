package com.fincity.saas.notification.feign;

import com.fincity.saas.notification.oserver.core.document.Connection;
import com.fincity.saas.notification.oserver.core.document.Notification;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import reactivefeign.spring.config.ReactiveFeignClient;
import reactor.core.publisher.Mono;

@ReactiveFeignClient(name = "core")
public interface IFeignCoreService {

    String CONNECTION_PATH = "/api/core/connections/internal";

    String NOTIFICATION_PATH = "/api/core/notifications/internal";

    @GetMapping(CONNECTION_PATH)
    Mono<Connection> getConnection(
            @RequestParam String urlClientCode,
            @RequestParam String connectionName,
            @RequestParam String appCode,
            @RequestParam String clientCode,
            @RequestParam String connectionType);

    @GetMapping(NOTIFICATION_PATH)
    Mono<Notification> getNotification(
            @RequestParam String urlClientCode,
            @RequestParam String notificationName,
            @RequestParam String appCode,
            @RequestParam String clientCode);
}
