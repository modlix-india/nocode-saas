package com.modlix.saas.notification.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import com.modlix.saas.notification.model.CoreNotification;
import com.modlix.saas.notification.model.NotificationConnectionDetails;

@FeignClient(name = "core")
public interface IFeignCoreService {

    @GetMapping("/api/core/connections/internal/notification/{name}")
    NotificationConnectionDetails getNotificationConnection(
            @PathVariable("name") String connectionName,
            @RequestParam String appCode,
            @RequestParam String clientCode,
            @RequestParam String urlClientCode);

    @GetMapping("/api/core/notifications/internal/{name}")
    CoreNotification getNotification(
            @PathVariable("name") String notificationName,
            @RequestParam String appCode,
            @RequestParam String clientCode,
            @RequestParam String urlClientCode);
}
