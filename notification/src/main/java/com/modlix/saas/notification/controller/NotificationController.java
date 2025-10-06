package com.modlix.saas.notification.controller;

import com.modlix.saas.notification.service.NotificationPreferenceService;

import java.math.BigInteger;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("api/notification")
public class NotificationController {

    private final NotificationPreferenceService notificationPreferenceService;

    public NotificationController(NotificationPreferenceService notificationPreferenceService) {
        this.notificationPreferenceService = notificationPreferenceService;
    }
    
    
    @GetMapping
    public ResponseEntity<Map<String, Object>> getNotificationPreference(@RequestParam String appCode, @RequestParam(required = false) BigInteger userId) {
        return ResponseEntity.ok(this.notificationPreferenceService.getNotificationPreference(appCode, userId));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> setNotificationPreference(@RequestParam String appCode, @RequestParam(required = false) BigInteger userId, @RequestBody(required = false) Map<String, Object> preference) {
        return ResponseEntity.ok(this.notificationPreferenceService.setNotificationPreference(appCode, userId, preference));
    }
    
    
}
