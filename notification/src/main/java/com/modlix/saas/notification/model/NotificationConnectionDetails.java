package com.modlix.saas.notification.model;

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.ToString;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@AllArgsConstructor
@ToString
public class NotificationConnectionDetails implements Serializable {

    private boolean inApp;
    private Connection mail;
}
