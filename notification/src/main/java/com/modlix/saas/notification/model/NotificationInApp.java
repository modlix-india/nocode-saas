package com.modlix.saas.notification.model;

import lombok.Data;
import org.jooq.types.ULong;

import java.time.LocalDateTime;

@Data
public class NotificationInApp {

    private ULong id;
    private String title;
    private String message;
    private LocalDateTime createdAt;
    private LocalDateTime readAt;
    private String type;
    private String mimeUrl;
}