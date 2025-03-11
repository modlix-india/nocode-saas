package com.fincity.saas.notification.service;

import java.util.EnumMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fincity.saas.commons.service.CacheService;
import com.fincity.saas.notification.enums.NotificationChannelType;
import com.fincity.saas.notification.feign.IFeignCoreService;

import lombok.Getter;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Getter
@Service
public class NotificationConnectionService implements INotificationCacheService<Map<String, Object>> {

	private static final String NOTIFICATION_CONN_CACHE = "notificationConn";
	private static final String NOTIFICATION_CONNECTION_TYPE = "NOTIFICATION";

	private IFeignCoreService coreService;

	private CacheService cacheService;

	@Autowired
	public void setCoreService(IFeignCoreService coreService) {
		this.coreService = coreService;
	}

	@Autowired
	public void setCacheService(CacheService cacheService) {
		this.cacheService = cacheService;
	}

	@Override
	public String getCacheName() {
		return NOTIFICATION_CONN_CACHE;
	}

	public Mono<Map<NotificationChannelType, Map<String, Object>>> getNotificationConnections(String appCode,
			String clientCode, Map<NotificationChannelType, String> channelConnections) {

		if (channelConnections == null || channelConnections.isEmpty())
			return Mono.empty();

		Map<NotificationChannelType, Map<String, Object>> connections = new EnumMap<>(NotificationChannelType.class);

		return Flux.fromIterable(channelConnections.entrySet())
				.flatMap(
						connection -> this
								.getNotificationConn(appCode, clientCode, connection.getValue())
								.filter(connectionMap -> !(connectionMap == null || connectionMap.isEmpty()))
								.doOnNext(connDetails -> connections.put(connection.getKey(), connDetails)))
				.then(Mono.just(connections));
	}

	public Mono<Map<String, Object>> getNotificationConn(String appCode, String clientCode, String connectionName) {
		return this.cacheValue(
				() -> coreService.getConnection(connectionName, appCode, clientCode, NOTIFICATION_CONNECTION_TYPE),
				appCode, clientCode, connectionName);
	}

}
