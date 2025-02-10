/*
 * This file is generated by jOOQ.
 */
package com.fincity.saas.notification.jooq;


import com.fincity.saas.notification.jooq.tables.NotificationAppPreference;
import com.fincity.saas.notification.jooq.tables.NotificationConnection;
import com.fincity.saas.notification.jooq.tables.NotificationTemplate;
import com.fincity.saas.notification.jooq.tables.NotificationType;
import com.fincity.saas.notification.jooq.tables.NotificationUserPreference;
import com.fincity.saas.notification.jooq.tables.records.NotificationAppPreferenceRecord;
import com.fincity.saas.notification.jooq.tables.records.NotificationConnectionRecord;
import com.fincity.saas.notification.jooq.tables.records.NotificationTemplateRecord;
import com.fincity.saas.notification.jooq.tables.records.NotificationTypeRecord;
import com.fincity.saas.notification.jooq.tables.records.NotificationUserPreferenceRecord;

import org.jooq.ForeignKey;
import org.jooq.TableField;
import org.jooq.UniqueKey;
import org.jooq.impl.DSL;
import org.jooq.impl.Internal;


/**
 * A class modelling foreign key relationships and constraints of tables in
 * notification.
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes", "this-escape" })
public class Keys {

    // -------------------------------------------------------------------------
    // UNIQUE and PRIMARY KEY definitions
    // -------------------------------------------------------------------------

    public static final UniqueKey<NotificationAppPreferenceRecord> KEY_NOTIFICATION_APP_PREFERENCE_PRIMARY = Internal.createUniqueKey(NotificationAppPreference.NOTIFICATION_APP_PREFERENCE, DSL.name("KEY_notification_app_preference_PRIMARY"), new TableField[] { NotificationAppPreference.NOTIFICATION_APP_PREFERENCE.ID }, true);
    public static final UniqueKey<NotificationAppPreferenceRecord> KEY_NOTIFICATION_APP_PREFERENCE_UK1_USER_PREFERENCE_CLIENT_ID_APP_ID_NOTI_TYPE = Internal.createUniqueKey(NotificationAppPreference.NOTIFICATION_APP_PREFERENCE, DSL.name("KEY_notification_app_preference_UK1_USER_PREFERENCE_CLIENT_ID_APP_ID_NOTI_TYPE"), new TableField[] { NotificationAppPreference.NOTIFICATION_APP_PREFERENCE.CLIENT_ID, NotificationAppPreference.NOTIFICATION_APP_PREFERENCE.APP_ID, NotificationAppPreference.NOTIFICATION_APP_PREFERENCE.NOTIFICATION_TYPE_ID }, true);
    public static final UniqueKey<NotificationConnectionRecord> KEY_NOTIFICATION_CONNECTION_PRIMARY = Internal.createUniqueKey(NotificationConnection.NOTIFICATION_CONNECTION, DSL.name("KEY_notification_connection_PRIMARY"), new TableField[] { NotificationConnection.NOTIFICATION_CONNECTION.ID }, true);
    public static final UniqueKey<NotificationConnectionRecord> KEY_NOTIFICATION_CONNECTION_UK1_NOTIFICATION_CONN_CODE_CLIENT_ID_APP_ID = Internal.createUniqueKey(NotificationConnection.NOTIFICATION_CONNECTION, DSL.name("KEY_notification_connection_UK1_NOTIFICATION_CONN_CODE_CLIENT_ID_APP_ID"), new TableField[] { NotificationConnection.NOTIFICATION_CONNECTION.CODE, NotificationConnection.NOTIFICATION_CONNECTION.CLIENT_ID, NotificationConnection.NOTIFICATION_CONNECTION.APP_ID }, true);
    public static final UniqueKey<NotificationTemplateRecord> KEY_NOTIFICATION_TEMPLATE_PRIMARY = Internal.createUniqueKey(NotificationTemplate.NOTIFICATION_TEMPLATE, DSL.name("KEY_notification_template_PRIMARY"), new TableField[] { NotificationTemplate.NOTIFICATION_TEMPLATE.ID }, true);
    public static final UniqueKey<NotificationTemplateRecord> KEY_NOTIFICATION_TEMPLATE_UK1_NOTIFICATION_TYPE_CODE_CLIENT_ID_APP_ID = Internal.createUniqueKey(NotificationTemplate.NOTIFICATION_TEMPLATE, DSL.name("KEY_notification_template_UK1_NOTIFICATION_TYPE_CODE_CLIENT_ID_APP_ID"), new TableField[] { NotificationTemplate.NOTIFICATION_TEMPLATE.CODE, NotificationTemplate.NOTIFICATION_TEMPLATE.CLIENT_ID, NotificationTemplate.NOTIFICATION_TEMPLATE.APP_ID }, true);
    public static final UniqueKey<NotificationTypeRecord> KEY_NOTIFICATION_TYPE_PRIMARY = Internal.createUniqueKey(NotificationType.NOTIFICATION_TYPE, DSL.name("KEY_notification_type_PRIMARY"), new TableField[] { NotificationType.NOTIFICATION_TYPE.ID }, true);
    public static final UniqueKey<NotificationTypeRecord> KEY_NOTIFICATION_TYPE_UK1_NOTIFICATION_TYPE_CODE_CLIENT_ID_APP_ID = Internal.createUniqueKey(NotificationType.NOTIFICATION_TYPE, DSL.name("KEY_notification_type_UK1_NOTIFICATION_TYPE_CODE_CLIENT_ID_APP_ID"), new TableField[] { NotificationType.NOTIFICATION_TYPE.CODE, NotificationType.NOTIFICATION_TYPE.CLIENT_ID, NotificationType.NOTIFICATION_TYPE.APP_ID }, true);
    public static final UniqueKey<NotificationUserPreferenceRecord> KEY_NOTIFICATION_USER_PREFERENCE_PRIMARY = Internal.createUniqueKey(NotificationUserPreference.NOTIFICATION_USER_PREFERENCE, DSL.name("KEY_notification_user_preference_PRIMARY"), new TableField[] { NotificationUserPreference.NOTIFICATION_USER_PREFERENCE.ID }, true);
    public static final UniqueKey<NotificationUserPreferenceRecord> KEY_NOTIFICATION_USER_PREFERENCE_UK1_USER_PREFERENCE_APP_ID_USER_ID_NOTI_TYPE = Internal.createUniqueKey(NotificationUserPreference.NOTIFICATION_USER_PREFERENCE, DSL.name("KEY_notification_user_preference_UK1_USER_PREFERENCE_APP_ID_USER_ID_NOTI_TYPE"), new TableField[] { NotificationUserPreference.NOTIFICATION_USER_PREFERENCE.APP_ID, NotificationUserPreference.NOTIFICATION_USER_PREFERENCE.USER_ID, NotificationUserPreference.NOTIFICATION_USER_PREFERENCE.NOTIFICATION_TYPE_ID }, true);

    // -------------------------------------------------------------------------
    // FOREIGN KEY definitions
    // -------------------------------------------------------------------------

    public static final ForeignKey<NotificationAppPreferenceRecord, NotificationTypeRecord> FK1_APP_PREF_NOTIFICATION_TYPE = Internal.createForeignKey(NotificationAppPreference.NOTIFICATION_APP_PREFERENCE, DSL.name("FK1_APP_PREF_NOTIFICATION_TYPE"), new TableField[] { NotificationAppPreference.NOTIFICATION_APP_PREFERENCE.NOTIFICATION_TYPE_ID }, Keys.KEY_NOTIFICATION_TYPE_PRIMARY, new TableField[] { NotificationType.NOTIFICATION_TYPE.ID }, true);
    public static final ForeignKey<NotificationUserPreferenceRecord, NotificationTypeRecord> FK1_USER_PREF_NOTIFICATION_TYPE = Internal.createForeignKey(NotificationUserPreference.NOTIFICATION_USER_PREFERENCE, DSL.name("FK1_USER_PREF_NOTIFICATION_TYPE"), new TableField[] { NotificationUserPreference.NOTIFICATION_USER_PREFERENCE.NOTIFICATION_TYPE_ID }, Keys.KEY_NOTIFICATION_TYPE_PRIMARY, new TableField[] { NotificationType.NOTIFICATION_TYPE.ID }, true);
}
