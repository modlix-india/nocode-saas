package com.fincity.saas.core.controller;

import com.fincity.saas.commons.core.document.Notification;
import com.fincity.saas.commons.core.repository.NotificationRepository;
import com.fincity.saas.commons.core.service.NotificationService;
import com.fincity.saas.commons.mongo.controller.AbstractOverridableDataController;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("api/core/notifications")
public class NotificationController
        extends AbstractOverridableDataController<Notification, NotificationRepository, NotificationService> {

    @GetMapping("/internal/{name}")
    public Mono<ResponseEntity<Notification>> getNotification(
            @PathVariable("name") String notificationName,
            @RequestParam String appCode,
            @RequestParam String clientCode,
            @RequestParam String urlClientCode) {
        return this.service.getNotification(notificationName, appCode, urlClientCode, clientCode)
                .map(ResponseEntity::ok);
    }
}
