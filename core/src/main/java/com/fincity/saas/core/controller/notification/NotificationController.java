package com.fincity.saas.core.controller.notification;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.saas.commons.mongo.controller.AbstractOverridableDataController;
import com.fincity.saas.core.document.Notification;
import com.fincity.saas.core.repository.NotificationRepository;
import com.fincity.saas.core.service.notification.NotificationService;

@RestController
@RequestMapping("api/core/notifications")
public class NotificationController
        extends AbstractOverridableDataController<Notification, NotificationRepository, NotificationService> {
}
