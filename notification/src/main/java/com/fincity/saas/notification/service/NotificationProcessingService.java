package com.fincity.saas.notification.service;

import java.math.BigInteger;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.fincity.saas.notification.feign.IFeignCoreService;

import reactor.core.publisher.Mono;

@Service
public class NotificationProcessingService {

	private IFeignCoreService coreService;

	private UserPreferenceService userPreferenceService;

	public NotificationProcessingService(IFeignCoreService coreService, UserPreferenceService userPreferenceService) {
		this.coreService = coreService;
		this.userPreferenceService = userPreferenceService;
	}


	public Mono<Boolean> processNotification(String appCode, String clientCode, BigInteger userId,
	                                         String notificationName, String connectionName) {

		Map<String, Object> notificationInfo = coreService.getNotificationInfo(notificationName, appCode, clientCode);

	}

	private Mono<Map<String, Object>> getNotificationInfo(String appCode, String clientCode, String notificationName) {

	}


}
