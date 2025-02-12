package com.fincity.saas.notification.service.preferences;

import org.jooq.UpdatableRecord;
import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.jooq.service.AbstractJOOQUpdatableDataService;
import com.fincity.saas.commons.security.feign.IFeignSecurityService;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.service.CacheService;
import com.fincity.saas.notification.dao.preference.NotificationPreferenceDao;
import com.fincity.saas.notification.dto.prefrence.NotificationPreference;
import com.fincity.saas.notification.service.NotificationMessageResourceService;

import lombok.Getter;
import reactor.core.publisher.Mono;

@Service
public abstract class NotificationPreferenceService<R extends UpdatableRecord<R>, D extends NotificationPreference<D>,
		O extends NotificationPreferenceDao<R, D>> extends AbstractJOOQUpdatableDataService<R, ULong, D, O> {

	protected final NotificationMessageResourceService messageResourceService;
	private final CacheService cacheService;

	@Getter
	private IFeignSecurityService securityService;

	protected NotificationPreferenceService(NotificationMessageResourceService messageResourceService,
	                                        CacheService cacheService) {
		this.messageResourceService = messageResourceService;
		this.cacheService = cacheService;
	}

	@Autowired
	public void setSecurityService(IFeignSecurityService securityService) {
		this.securityService = securityService;
	}

	protected abstract String getPreferenceName();

	public abstract String getPreferenceCacheName();

	public abstract boolean isAppLevel();

	private String getCacheKeys(ULong clientId, ULong identifierId) {
		return clientId + ":" + identifierId;
	}

	public Mono<D> create(D entity) {

		return FlatMapUtil.flatMapMono(

				SecurityContextUtil::getUsersContextAuthentication,

		)
	}

	private Mono<D> updateIdentifiers(ContextAuthentication ca, D entity) {

		if (isAppLevel()) {
			return FlatMapUtil.flatMapMono(



			)
		}

		return FlatMapUtil.flatMapMono(
				() -> this.securityService.getAppByCode(ca.getUrlAppCode()),

				app ->
		)

	}


}
