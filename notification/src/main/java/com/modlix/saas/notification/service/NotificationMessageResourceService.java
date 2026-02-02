package com.modlix.saas.notification.service;

import com.modlix.saas.commons2.configuration.service.AbstractMessageService;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

@Service
public class NotificationMessageResourceService extends AbstractMessageService {

    public static final String NOTIFICATION_PREFIX = "NOTIFICATION_";

    public static final String UKNOWN_ERROR = "unknown_error";
    public static final String FORBIDDEN_CREATE = "forbidden_create";
    public static final String FORBIDDEN_UPDATE = "forbidden_update";
    public static final String TEMPLATE_DATA_NOT_FOUND = "template_data_not_found";
    public static final String CONNECTION_DETAILS_MISSING = "connection_details_missing";
    public static final String MAIL_SEND_ERROR = "mail_send_error";

    protected NotificationMessageResourceService() {
        super(Map.of(Locale.ENGLISH, ResourceBundle.getBundle("messages", Locale.ENGLISH)));
    }
}