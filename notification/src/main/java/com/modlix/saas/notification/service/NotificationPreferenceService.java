package com.modlix.saas.notification.service;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.Map;

import org.jooq.DSLContext;
import org.jooq.types.ULong;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.modlix.saas.commons2.exception.GenericException;
import com.modlix.saas.commons2.security.feign.IFeignSecurityService;
import com.modlix.saas.commons2.security.jwt.ContextAuthentication;
import com.modlix.saas.commons2.security.util.SecurityContextUtil;
import com.modlix.saas.commons2.service.CacheService;
import com.modlix.saas.notification.jooq.tables.NotificationPreference;
import com.modlix.saas.notification.jooq.tables.records.NotificationPreferenceRecord;

@Service
public class NotificationPreferenceService {

    public static final String CACHE_NAME_NOTIFICATION_PREFERENCE = "notificationPreference";

    private final DSLContext dslContext;
    
    private final IFeignSecurityService securityService;

    private final CacheService cacheService;

    public NotificationPreferenceService(IFeignSecurityService securityService, DSLContext dslContext, CacheService cacheService) {
        this.dslContext = dslContext;
        this.securityService = securityService;
        this.cacheService = cacheService;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getNotificationPreference(String appCode, BigInteger userId) {

        ULong validatedUserId = getValidatedUserId(userId);

        NotificationPreferenceRecord pref = this.getNotificationPreferenceRecord(appCode, validatedUserId);

        return pref != null && pref.getPreference() != null ? (Map<String, Object>) pref.getPreference() : Map.of();
    }

    private NotificationPreferenceRecord getNotificationPreferenceRecord(String appCode, ULong userId) {
       
       return this.dslContext.selectFrom(NotificationPreference.NOTIFICATION_PREFERENCE)
            .where(NotificationPreference.NOTIFICATION_PREFERENCE.USER_ID.eq(userId))
            .and(NotificationPreference.NOTIFICATION_PREFERENCE.APP_CODE.eq(appCode)).fetchOne();
    }

    private ULong getValidatedUserId(BigInteger userId) {
        ULong ulongUserId = null;
        if (userId == null) {
            ulongUserId = ULong.valueOf(SecurityContextUtil.getUsersContextUser().getId());
        }else {
            
            Boolean isManaged = this.securityService.isUserBeingManaged(userId, SecurityContextUtil.getUsersContextAuthentication().getClientCode());
            if (!isManaged.booleanValue()) {
                throw new GenericException(HttpStatus.FORBIDDEN, "User don't have access to this user Id : " + userId);
            }
            ulongUserId = ULong.valueOf(userId);
        }
        return ulongUserId;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> setNotificationPreference(String appCode, BigInteger userId, Map<String, Object> preference) {

        ULong validatedUserId = getValidatedUserId(userId);
        this.cacheService.evict(CACHE_NAME_NOTIFICATION_PREFERENCE, appCode, validatedUserId);

        NotificationPreferenceRecord pref = this.getNotificationPreferenceRecord(appCode, validatedUserId);

        ContextAuthentication auth = SecurityContextUtil.getUsersContextAuthentication();

        if (pref == null) {
            pref = this.dslContext.newRecord(NotificationPreference.NOTIFICATION_PREFERENCE);
            pref.setUserId(validatedUserId);
            pref.setAppCode(appCode);
            pref.setPreference(preference);
            pref.setCreatedBy(ULong.valueOf(auth.getUser().getId()));
            pref.setCreatedAt(LocalDateTime.now());
            pref.setUpdatedBy(pref.getCreatedBy());
            pref.setUpdatedAt(pref.getCreatedAt());
            this.dslContext.insertInto(NotificationPreference.NOTIFICATION_PREFERENCE).set(pref).execute();
        }else {

            if (preference == null || preference.isEmpty()) {
                this.dslContext.deleteFrom(NotificationPreference.NOTIFICATION_PREFERENCE)
                    .where(NotificationPreference.NOTIFICATION_PREFERENCE.ID.eq(pref.getId())).execute();
                return Map.of();
            }

            pref.setPreference(preference);
            pref.setUpdatedBy(ULong.valueOf(auth.getUser().getId()));
            pref.setUpdatedAt(LocalDateTime.now());
            this.dslContext.update(NotificationPreference.NOTIFICATION_PREFERENCE).set(pref)
                .where(NotificationPreference.NOTIFICATION_PREFERENCE.ID.eq(pref.getId())).execute();
        }

        return pref.getPreference();
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getInternalNotificationPreferecene(String appCode, ULong userId) {
        return this.cacheService.cacheValueOrGet(CACHE_NAME_NOTIFICATION_PREFERENCE, 
        () -> {
            NotificationPreferenceRecord pref = this.getNotificationPreferenceRecord(appCode, userId);
            return pref != null && pref.getPreference() != null ? (Map<String, Object>) pref.getPreference() : Map.of();
        }, appCode, userId);
    }
}
