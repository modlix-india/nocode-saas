package com.modlix.saas.notification.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.modlix.saas.commons2.security.jwt.ContextUser;
import com.modlix.saas.notification.model.NotificationInApp;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.jooq.types.ULong;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.stereotype.Service;

import com.modlix.saas.commons2.security.model.NotificationUser;
import com.modlix.saas.commons2.security.util.SecurityContextUtil;
import com.modlix.saas.commons2.util.StringUtil;
import com.modlix.saas.notification.jooq.tables.NotificationInapp;
import com.modlix.saas.notification.jooq.tables.records.NotificationInappRecord;
import com.modlix.saas.notification.model.CoreNotification;

@Service
public class InAppNotificationService extends AbstractTemplateService {

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(InAppNotificationService.class);

    private static final String INAPP_TEMPLATE_NAME = "inapp";

    private final DSLContext dslContext;

    public InAppNotificationService(DSLContext dslContext) {
        this.dslContext = dslContext;
    }

    public void sendInApp(List<NotificationUser> users, CoreNotification notification, String appCode, Map<String, Object> payload) {

        String language = this.getLanguage(notification, payload);

        Map<String, String> template = this.getProcessedTemplate(language, notification.getChannelTemplates().get(INAPP_TEMPLATE_NAME), payload);

        if (template == null || template.isEmpty())
            return;

        List<NotificationInappRecord> records = users.stream().map(e -> {
            NotificationInappRecord rec = this.dslContext.newRecord(NotificationInapp.NOTIFICATION_INAPP);
            rec.setUserId(ULong.valueOf(e.getId()));
            rec.setAppCode(appCode);
            rec.setNotificationName(notification.getName());
            rec.setNotificationType(notification.getNotificationType().name());
            rec.setTitle(template.get("title"));
            rec.setMessage(template.get("description"));
            rec.setMimeUrl(template.get("image"));
            return rec;
        }).toList();

        this.dslContext.batchInsert(records).execute();

        if (records.size() != users.size()) {
            logger.error("Failed to send in-app notification to all users");
        }
    }

    public int checkForNewNotifications(String appCode, String type) {

        if (StringUtil.safeIsBlank(appCode)) {
            return 0;
        }

        ContextUser cUser = SecurityContextUtil.getUsersContextUser();
        if (cUser == null) return 0;

        ULong userId = ULong.valueOf(cUser.getId());

        Condition condition = DSL.and(NotificationInapp.NOTIFICATION_INAPP.APP_CODE.eq(appCode),
                NotificationInapp.NOTIFICATION_INAPP.USER_ID.eq(userId), NotificationInapp.NOTIFICATION_INAPP.READ_AT.isNull());

        if (!StringUtil.safeIsBlank(type)) {
            condition = condition.and(NotificationInapp.NOTIFICATION_INAPP.NOTIFICATION_TYPE.eq(type));
        }

        Integer count = this.dslContext.selectCount().from(NotificationInapp.NOTIFICATION_INAPP).where(condition)
                .fetchOneInto(Integer.class);

        if (count == null) return 0;

        return count;
    }

    public Page<NotificationInApp> readNotifications(String appCode, String type, Pageable pageable) {

        ContextUser cUser = SecurityContextUtil.getUsersContextUser();

        if (StringUtil.safeIsBlank(appCode) || cUser == null) {
            return PageableExecutionUtils.getPage(List.of(), pageable, () -> -1);
        }

        ULong userId = ULong.valueOf(cUser.getId());

        Condition condition = DSL.and(NotificationInapp.NOTIFICATION_INAPP.APP_CODE.eq(appCode),
                NotificationInapp.NOTIFICATION_INAPP.USER_ID.eq(userId));

        if (!StringUtil.safeIsBlank(type)) {
            condition = condition.and(NotificationInapp.NOTIFICATION_INAPP.NOTIFICATION_TYPE.eq(type));
        }

        List<NotificationInApp> records = this.dslContext.selectFrom(NotificationInapp.NOTIFICATION_INAPP)
                .where(condition)
                .orderBy(NotificationInapp.NOTIFICATION_INAPP.CREATED_AT.desc())
                .limit(pageable.getPageSize())
                .offset(pageable.getOffset())
                .fetchInto(NotificationInApp.class);

        if (!records.isEmpty()) {
            this.dslContext.update(NotificationInapp.NOTIFICATION_INAPP)
                    .set(NotificationInapp.NOTIFICATION_INAPP.READ_AT, LocalDateTime.now())
                    .where(DSL.and(

                            NotificationInapp.NOTIFICATION_INAPP.ID.in(records.stream().map(NotificationInApp::getId).toList())))
                    .execute();
        }

        return PageableExecutionUtils.getPage(records, pageable, () -> -1);
    }
}