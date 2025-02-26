package com.fincity.saas.notification.service;

import java.math.BigInteger;
import java.util.Map;

import org.jooq.types.ULong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fincity.nocode.reactor.util.FlatMapUtil;
import com.fincity.saas.commons.jooq.util.ULongUtil;
import com.fincity.saas.commons.security.jwt.ContextAuthentication;
import com.fincity.saas.commons.security.util.SecurityContextUtil;
import com.fincity.saas.commons.service.CacheService;
import com.fincity.saas.commons.util.StringUtil;
import com.fincity.saas.notification.feign.IFeignCoreService;

import lombok.Getter;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple3;
import reactor.util.function.Tuples;

@Service
public class NotificationProcessingService {

	public static final String NOTIFICATION_INFO_CACHE = "notificationInfo";

	private final IFeignCoreService coreService;
	@Getter
	private CacheService cacheService;
	private UserPreferenceService userPreferenceService;

	public NotificationProcessingService(IFeignCoreService coreService, UserPreferenceService userPreferenceService) {
		this.coreService = coreService;
		this.userPreferenceService = userPreferenceService;
	}

	@Autowired
	public void setCacheService(CacheService cacheService) {
		this.cacheService = cacheService;
	}

	private String getCacheKeys(String... entityNames) {
		return String.join(":", entityNames);
	}


	private String getNotificationInfoCache() {
		return NOTIFICATION_INFO_CACHE;
	}

	public Mono<Boolean> processNotification(String appCode, String clientCode, BigInteger userId,
	                                         String notificationName) {

		return FlatMapUtil.flatMapMono(

				SecurityContextUtil::getUsersContextAuthentication,

				ca -> this.getAppClientUserEntity(ca, appCode, clientCode, userId),

				(ca, userEntity) -> this.userPreferenceService.getNotificationConn(appCode, clientCode, connectionName),

				(ca, userEntity, conn) -> this.getNotificationInfo(userEntity.getT1(), userEntity.getT2(), notificationName),

				(ca, userEntity, conn, notificationInfo) -> {

				}

				);

	}

	private Mono<Map<String, Object>> getNotificationInfo(String appCode, String clientCode, String notificationName) {
		return cacheService.cacheValueOrGet(this.getNotificationInfoCache(),
				() -> coreService.getNotificationInfo(notificationName, appCode, clientCode),
				this.getCacheKeys(appCode, clientCode, notificationName));
	}

	private Mono<Tuple3<String, String, ULong>> getAppClientUserEntity(ContextAuthentication ca, String appCode,
	                                                                   String clientCode, BigInteger userId) {
		return Mono.just(Tuples.of(
				StringUtil.safeIsBlank(appCode) ? ca.getUrlAppCode() : appCode,
				StringUtil.safeIsBlank(clientCode) ? ca.getUrlClientCode() : clientCode,
				ULongUtil.valueOf(userId == null ? ca.getUser().getId() : userId)));
	}


}
