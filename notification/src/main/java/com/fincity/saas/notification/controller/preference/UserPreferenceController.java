package com.fincity.saas.notification.controller.preference;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.saas.notification.dao.preference.UserPreferenceDao;
import com.fincity.saas.notification.dto.prefrence.UserPreference;
import com.fincity.saas.notification.jooq.tables.records.NotificationUserPreferenceRecord;
import com.fincity.saas.notification.service.preferences.UserPreferenceService;

@RestController
@RequestMapping("api/notifications/preferences/users")
public class UserPreferenceController extends
		NotificationPreferenceController<NotificationUserPreferenceRecord, UserPreference, UserPreferenceDao, UserPreferenceService> {

}
