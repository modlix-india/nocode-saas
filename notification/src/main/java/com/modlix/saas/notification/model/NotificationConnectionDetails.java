package com.modlix.saas.notification.model;

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.experimental.Accessors;

@Data 
@Accessors(chain = true)
@AllArgsConstructor
public class NotificationConnectionDetails implements Serializable {

    private boolean inApp;
    private Connection mail;
}
