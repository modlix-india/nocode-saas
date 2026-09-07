package com.modlix.saas.notification.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import com.modlix.saas.notification.model.CoreNotification;
import com.modlix.saas.notification.model.NotificationConnectionDetails;

/**
 * Definition lookups only.
 *
 * The x-draft header is set explicitly on these two calls rather than through the
 * module's Feign RequestInterceptor, which would apply it to EVERY client
 * including IFeignSecurityService. The draft surface should resolve the draft
 * notification and connection DEFINITION; it must not touch how recipients are
 * resolved, which stays the real user directory.
 */
@FeignClient(name = "core")
public interface IFeignCoreService {

    @GetMapping("/api/core/connections/internal/notification/{name}")
    NotificationConnectionDetails getNotificationConnection(
            @PathVariable("name") String connectionName,
            @RequestParam String appCode,
            @RequestParam String clientCode,
            @RequestParam String urlClientCode,
            @RequestHeader(name = "x-draft", required = false) String draft);

    @GetMapping("/api/core/notifications/internal/{name}")
    CoreNotification getNotification(
            @PathVariable("name") String notificationName,
            @RequestParam String appCode,
            @RequestParam String clientCode,
            @RequestParam String urlClientCode,
            @RequestHeader(name = "x-draft", required = false) String draft);
}
