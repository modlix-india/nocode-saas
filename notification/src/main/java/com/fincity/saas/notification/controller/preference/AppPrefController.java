package com.fincity.saas.notification.controller.preference;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.saas.notification.dao.preference.AppPreferenceDao;
import com.fincity.saas.notification.dto.prefrence.AppPreference;
import com.fincity.saas.notification.jooq.tables.records.NotificationAppPreferenceRecord;
import com.fincity.saas.notification.service.preferences.AppPreferenceService;

@RestController
@RequestMapping("api/notifications/preferences/app")
public class AppPrefController extends
		NotificationPrefController<NotificationAppPreferenceRecord, AppPreference, AppPreferenceDao, AppPreferenceService> {
}
