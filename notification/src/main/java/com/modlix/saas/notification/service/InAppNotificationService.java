package com.modlix.saas.notification.service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.jooq.types.ULong;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.support.PageableExecutionUtils;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.modlix.saas.commons2.security.jwt.ContextUser;
import com.modlix.saas.commons2.security.model.NotificationUser;
import com.modlix.saas.commons2.security.util.SecurityContextUtil;
import com.modlix.saas.commons2.util.StringUtil;
import com.modlix.saas.notification.jooq.tables.NotificationInapp;
import com.modlix.saas.notification.jooq.tables.records.NotificationInappRecord;
import com.modlix.saas.notification.model.CoreNotification;
import com.modlix.saas.notification.model.NotificationInApp;

import jakarta.servlet.http.HttpServletResponse;

@Service
public class InAppNotificationService extends AbstractTemplateService {

    private static final String INAPP_TEMPLATE_NAME = "inapp";

    private final Map<String, ConcurrentLinkedQueue<SseEmitter>> userEmitters = new ConcurrentHashMap<>();

    private final DSLContext dslContext;

    private final ObjectMapper objectMapper;

    private static record NotificationEventRecord(ULong id, ULong userId, String appCode, String notificationName,
                                                  String title, String message, String mimeUrl,
                                                  String notificationType) {
        public static NotificationEventRecord fromRecord(NotificationInappRecord record) {
            return new NotificationEventRecord(record.getId(), record.getUserId(), record.getAppCode(), record.getNotificationName(), record.getTitle(), record.getMessage(), record.getMimeUrl(), record.getNotificationType());
        }
    }

    public InAppNotificationService(DSLContext dslContext, ObjectMapper objectMapper) {
        this.dslContext = dslContext;
        this.objectMapper = objectMapper;
    }

    public void sendInApp(List<NotificationUser> users, CoreNotification notification, String appCode,
                          Map<String, Object> payload) {

        String language = this.getLanguage(notification, payload);

        Map<String, String> template = this.getProcessedTemplate(language,
                notification.getChannelTemplates().get(INAPP_TEMPLATE_NAME), payload);

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
        }).map(nr -> {
            return this.dslContext.insertInto(NotificationInapp.NOTIFICATION_INAPP).set(nr).returning().fetchOne();
        }).toList();

        records.forEach(nr -> {
            String userId = nr.getUserId().toString() + "-" + appCode;
            ConcurrentLinkedQueue<SseEmitter> emitter = userEmitters.get(userId);
            if (emitter != null) {

                String jsonString = null;

                try {
                    jsonString = objectMapper.writeValueAsString(NotificationEventRecord.fromRecord(nr));
                } catch (Exception e) {
                    return;
                }

                if (StringUtil.safeIsBlank(jsonString)) return;
                String finalJsonString = jsonString;

                emitter.forEach(em -> {
                    try {
                        em.send(SseEmitter.event().name("notification").data(finalJsonString)
                                .id(UUID.randomUUID().toString())
                                .reconnectTime(5000));
                    } catch (IOException ignored) {
                    }
                });
            }
        });
    }

    @Scheduled(fixedRate = 15000)
    public void heartbeat() {
        userEmitters.values().stream().flatMap(ConcurrentLinkedQueue::stream).forEach(em -> {
            try {
                em.send(SseEmitter.event().comment("keepalive"));
            } catch (IOException ignored) {
            }
        });
    }

    public int checkForNewNotifications(String appCode, String type) {

        if (StringUtil.safeIsBlank(appCode)) {
            return 0;
        }

        ContextUser cUser = SecurityContextUtil.getUsersContextUser();
        if (cUser == null)
            return 0;

        ULong userId = ULong.valueOf(cUser.getId());

        Condition condition = DSL.and(NotificationInapp.NOTIFICATION_INAPP.APP_CODE.eq(appCode),
                NotificationInapp.NOTIFICATION_INAPP.USER_ID.eq(userId),
                NotificationInapp.NOTIFICATION_INAPP.READ_AT.isNull());

        if (!StringUtil.safeIsBlank(type)) {
            condition = condition.and(NotificationInapp.NOTIFICATION_INAPP.NOTIFICATION_TYPE.eq(type));
        }

        Integer count = this.dslContext.selectCount().from(NotificationInapp.NOTIFICATION_INAPP).where(condition)
                .fetchOneInto(Integer.class);

        if (count == null)
            return 0;

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

                            NotificationInapp.NOTIFICATION_INAPP.ID
                                    .in(records.stream().map(NotificationInApp::getId).toList())))
                    .execute();
        }

        return PageableExecutionUtils.getPage(records, pageable, () -> -1);
    }

    public SseEmitter subscribe(String appCode, HttpServletResponse res) {

        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);

        String userId = SecurityContextUtil.getUsersContextUser().getId().toString() + "-" + appCode;

        emitter.onCompletion(() -> {
            ConcurrentLinkedQueue<SseEmitter> list = userEmitters.get(userId);
            if (list != null) {
                list.remove(emitter);
            }
        });
        emitter.onTimeout(() -> {
            ConcurrentLinkedQueue<SseEmitter> list = userEmitters.get(userId);
            if (list != null) {
                list.remove(emitter);
            }
        });
        emitter.onError(e -> {
            ConcurrentLinkedQueue<SseEmitter> list = userEmitters.get(userId);
            if (list != null) {
                list.remove(emitter);
            }
        });

        if (!userEmitters.containsKey(userId)) {
            ConcurrentLinkedQueue<SseEmitter> list = new ConcurrentLinkedQueue<>();
            list.add(emitter);
            userEmitters.put(userId, list);
        } else {
            userEmitters.get(userId).add(emitter);
        }

        try {
            emitter.send(SseEmitter.event().name("init").data("connected").id(UUID.randomUUID().toString())
                    .reconnectTime(5000));
            res.setContentType(MediaType.TEXT_EVENT_STREAM_VALUE);
            res.setHeader("Cache-Control", "no-cache");
            res.setHeader("Connection", "keep-alive");
            res.setHeader("X-Accel-Buffering", "no");
            res.setCharacterEncoding("UTF-8");
            res.flushBuffer(); // << force headers out now
        } catch (IOException e) {
            logger.error("Failed initial SSE send", e);
            emitter.completeWithError(e);
        }
        return emitter;
    }
}
