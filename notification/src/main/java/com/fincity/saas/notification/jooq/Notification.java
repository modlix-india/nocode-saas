/*
 * This file is generated by jOOQ.
 */
package com.fincity.saas.notification.jooq;


import com.fincity.saas.notification.jooq.tables.NotificationInAppNotifications;
import com.fincity.saas.notification.jooq.tables.NotificationSentNotifications;
import com.fincity.saas.notification.jooq.tables.NotificationUserPreferences;

import java.util.Arrays;
import java.util.List;

import org.jooq.Catalog;
import org.jooq.Table;
import org.jooq.impl.SchemaImpl;


/**
 * This class is generated by jOOQ.
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes", "this-escape" })
public class Notification extends SchemaImpl {

    private static final long serialVersionUID = 1L;

    /**
     * The reference instance of <code>notification</code>
     */
    public static final Notification NOTIFICATION = new Notification();

    /**
     * The table <code>notification.notification_in_app_notifications</code>.
     */
    public final NotificationInAppNotifications NOTIFICATION_IN_APP_NOTIFICATIONS = NotificationInAppNotifications.NOTIFICATION_IN_APP_NOTIFICATIONS;

    /**
     * The table <code>notification.notification_sent_notifications</code>.
     */
    public final NotificationSentNotifications NOTIFICATION_SENT_NOTIFICATIONS = NotificationSentNotifications.NOTIFICATION_SENT_NOTIFICATIONS;

    /**
     * The table <code>notification.notification_user_preferences</code>.
     */
    public final NotificationUserPreferences NOTIFICATION_USER_PREFERENCES = NotificationUserPreferences.NOTIFICATION_USER_PREFERENCES;

    /**
     * No further instances allowed
     */
    private Notification() {
        super("notification", null);
    }


    @Override
    public Catalog getCatalog() {
        return DefaultCatalog.DEFAULT_CATALOG;
    }

    @Override
    public final List<Table<?>> getTables() {
        return Arrays.asList(
            NotificationInAppNotifications.NOTIFICATION_IN_APP_NOTIFICATIONS,
            NotificationSentNotifications.NOTIFICATION_SENT_NOTIFICATIONS,
            NotificationUserPreferences.NOTIFICATION_USER_PREFERENCES
        );
    }
}
