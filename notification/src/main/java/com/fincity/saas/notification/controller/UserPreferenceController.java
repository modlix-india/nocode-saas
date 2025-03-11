package com.fincity.saas.notification.controller;

import org.jooq.types.ULong;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fincity.saas.notification.dao.UserPreferenceDao;
import com.fincity.saas.notification.dto.UserPreference;
import com.fincity.saas.notification.jooq.tables.records.NotificationUserPreferencesRecord;
import com.fincity.saas.notification.service.UserPreferenceService;

import reactor.core.publisher.Mono;

@RestController
@RequestMapping("api/notifications/preferences/users")
public class UserPreferenceController extends AbstractCodeController<NotificationUserPreferencesRecord, ULong,
		UserPreference, UserPreferenceDao, UserPreferenceService> implements INotificationCacheController<UserPreference, UserPreferenceService> {

	@Override
	public UserPreferenceService getService() {
		return this.service;
	}

	@GetMapping("/me")
	public Mono<ResponseEntity<UserPreference>> getCurrentUserPreference() {
		return this.service.getUserPreference().map(ResponseEntity::ok)
				.switchIfEmpty(Mono.defer(() -> Mono.just(ResponseEntity.notFound().build())));
	}

	@PostMapping("/me")
	public Mono<UserPreference> createCurrentUserPreference(@RequestBody UserPreference entity) {
		return this.service.create(entity);
	}
}
