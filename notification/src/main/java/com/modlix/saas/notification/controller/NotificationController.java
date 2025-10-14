package com.modlix.saas.notification.controller;

import com.modlix.saas.commons2.util.StringUtil;
import com.modlix.saas.notification.jooq.tables.records.NotificationInappRecord;
import com.modlix.saas.notification.model.NotificationInApp;
import com.modlix.saas.notification.service.InAppNotificationService;
import com.modlix.saas.notification.service.NotificationPreferenceService;

import java.math.BigInteger;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api/notification")
public class NotificationController {

    private final NotificationPreferenceService notificationPreferenceService;
    private final InAppNotificationService inAppNotificationService;

    public NotificationController(NotificationPreferenceService notificationPreferenceService,
                                  InAppNotificationService inAppNotificationService) {
        this.notificationPreferenceService = notificationPreferenceService;
        this.inAppNotificationService = inAppNotificationService;
    }

    @GetMapping("/notifications")
    public ResponseEntity<Page<NotificationInApp>> getNotifications(
            @RequestParam(required = false, name = "appCode") String paramAppCode,
            @RequestHeader(required = false, name = "appCode") String headerAppCode,
            @RequestParam(required = false, name = "type") String type,
            Pageable pageable) {
        return ResponseEntity.ok(this.inAppNotificationService
                .readNotifications(StringUtil.safeValueOf(paramAppCode, headerAppCode), type, pageable));
    }

    @GetMapping("/new")
    public ResponseEntity<Integer> checkForNewNotifications(
            @RequestParam(required = false, name = "appCode") String paramAppCode,
            @RequestHeader(required = false, name = "appCode") String headerAppCode,
            @RequestParam(required = false, name = "type") String type) {
        return ResponseEntity.ok(this.inAppNotificationService
                .checkForNewNotifications(StringUtil.safeValueOf(paramAppCode, headerAppCode), type));
    }

    @GetMapping("/preference")
    public ResponseEntity<Map<String, Object>> getNotificationPreference(@RequestParam String appCode,
                                                                         @RequestParam(required = false) BigInteger userId) {
        return ResponseEntity.ok(this.notificationPreferenceService.getNotificationPreference(appCode, userId));
    }

    @PostMapping("/preference")
    public ResponseEntity<Map<String, Object>> setNotificationPreference(@RequestParam String appCode,
                                                                         @RequestParam(required = false) BigInteger userId,
                                                                         @RequestBody(required = false) Map<String, Object> preference) {
        return ResponseEntity
                .ok(this.notificationPreferenceService.setNotificationPreference(appCode, userId, preference));
    }

}
