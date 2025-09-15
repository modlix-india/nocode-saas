package com.fincity.saas.core.controller.notification;

import com.fincity.saas.commons.core.document.Notification;
import com.fincity.saas.commons.core.repository.NotificationRepository;
import com.fincity.saas.commons.core.service.notification.CoreNotificationService;
import com.fincity.saas.commons.mongo.controller.AbstractOverridableDataController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api/core/notifications")
public class NotificationController
        extends AbstractOverridableDataController<Notification, NotificationRepository, CoreNotificationService> {

}
