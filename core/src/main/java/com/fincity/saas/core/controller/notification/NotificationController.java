package com.fincity.saas.core.controller.notification;

import com.fincity.saas.commons.core.document.Storage;
import com.fincity.saas.commons.core.repository.NotificationRepository;
import com.fincity.saas.commons.core.service.notification.CoreNotificationService;
import com.fincity.saas.commons.mongo.controller.AbstractOverridableDataController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("api/core/notifications")
public class NotificationController
        extends AbstractOverridableDataController<Storage.Notification, NotificationRepository, CoreNotificationService> {

    @GetMapping("/internal")
    public Mono<Storage.Notification> getNotification(
            @RequestParam String notificationName, @RequestParam String appCode, @RequestParam String clientCode) {
        return this.service.readInternalNotification(notificationName, appCode, clientCode);
    }
}
