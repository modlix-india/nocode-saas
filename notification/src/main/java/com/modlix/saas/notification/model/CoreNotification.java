package com.modlix.saas.notification.model;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

import com.modlix.saas.notification.enums.NotificationType;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@NoArgsConstructor
public class CoreNotification implements Serializable {

    private NotificationType notificationType = NotificationType.INFO;
    private String defaultLanguage;
    private String languageExpression;

    private Map<String, Object> variableSchema; // NOSONAR

    private Map<String, NotificationTemplate> channelTemplates;
    private Map<String, String> channelConnections;
    private Map<String, Boolean> channelEnabled;
    private String name;

    @Data
    @Accessors(chain = true)
    @NoArgsConstructor
    public static class NotificationTemplate implements Serializable{

        @Serial
        private static final long serialVersionUID = 1054865111921742820L;

        private Map<String, Map<String, String>> templateParts;
        private Map<String, String> resources;
    }
}
