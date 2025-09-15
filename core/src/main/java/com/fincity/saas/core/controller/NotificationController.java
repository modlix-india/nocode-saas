package com.fincity.saas.core.controller;

import com.fincity.saas.commons.core.document.Notification;
import com.fincity.saas.commons.core.repository.NotificationRepository;
import com.fincity.saas.commons.core.service.NotificationService;
import com.fincity.saas.commons.mongo.controller.AbstractOverridableDataController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api/core/notifications")
public class NotificationController
        extends AbstractOverridableDataController<Notification, NotificationRepository, NotificationService> {

}
