package com.fincity.saas.notification.controller;

import org.jooq.types.ULong;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.saas.notification.dao.SentNotificationDao;
import com.fincity.saas.notification.dto.SentNotification;
import com.fincity.saas.notification.jooq.tables.records.NotificationSentNotificationsRecord;
import com.fincity.saas.notification.service.SentNotificationService;

@RestController
@RequestMapping("api/notifications/sent")
public class SentNotificationController extends
		AbstractCodeController<NotificationSentNotificationsRecord, ULong, SentNotification, SentNotificationDao, SentNotificationService> {

}
