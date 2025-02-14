package com.fincity.saas.notification.controller.preference;

import java.math.BigInteger;

import org.jooq.UpdatableRecord;
import org.jooq.types.ULong;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.fincity.saas.commons.jooq.controller.AbstractJOOQUpdatableDataController;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.saas.notification.dao.preference.NotificationPreferenceDao;
import com.fincity.saas.notification.dto.prefrence.NotificationPreference;
import com.fincity.saas.notification.service.preferences.NotificationPreferenceService;

import reactor.core.publisher.Mono;

public class NotificationPreferenceController<R extends UpdatableRecord<R>, D extends NotificationPreference<D>,
		O extends NotificationPreferenceDao<R, D>, S extends NotificationPreferenceService<R, D, O>>
		extends AbstractJOOQUpdatableDataController<R, ULong, D, O, S> {

	@GetMapping()
	public Mono<ResponseEntity<D>> getNotificationPreference(
			@RequestParam(name = "clientCode", required = false) String clientCode,
			@RequestParam(name = "id", required = false) BigInteger id,
			@RequestParam(name = "current", required = false) Boolean current) {

		if (!StringUtil.safeIsBlank(clientCode) && this.service.isAppLevel())
			return this.service.getNotificationPreference(clientCode, current != null ? current : Boolean.FALSE)
					.map(ResponseEntity::ok);

		return this.service.getNotificationPreference(id, current != null ? current : Boolean.FALSE)
				.map(ResponseEntity::ok);
	}

}
