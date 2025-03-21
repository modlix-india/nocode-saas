package com.fincity.saas.notification.service;

import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;

import com.fincity.saas.commons.service.CacheService;
import com.fincity.saas.notification.model.NotificationCacheRequest;

import reactor.core.publisher.Mono;

public interface INotificationCacheService<T> {

	String getCacheName();

	CacheService getCacheService();

	default String getCacheKey(Object... entityNames) {
		return String.join(":", Stream.of(entityNames).map(Object::toString).toArray(String[]::new));
	}

	default String getCacheKey(String... entityNames) {
		return String.join(":", entityNames);
	}

	default String getCacheKey(NotificationCacheRequest cacheRequest) {
		return this.getCacheKey(cacheRequest.getAppCode(), cacheRequest.getClientCode(), cacheRequest.getEntityName());
	}

	default Mono<Boolean> evictChannelEntities(Map<String, String> channelEntities) {
		return Mono.just(Boolean.TRUE);
	}

	default Mono<Boolean> evict(NotificationCacheRequest cacheRequest) {

		if (cacheRequest.hasChannelEntities())
			return Mono.zip(
					this.evictChannelEntities(cacheRequest.getChannelEntities()),
					this.getCacheService().evict(this.getCacheName(), this.getCacheKey(cacheRequest)),
					(entitiesEvicted, cacheEvicted) -> cacheEvicted);

		return this.getCacheService().evict(this.getCacheName(), this.getCacheKey(cacheRequest));
	}

	default Mono<T> cacheValueOrGet(Supplier<Mono<T>> supplier, Object... entityNames) {
		return this.getCacheService().cacheValueOrGet(this.getCacheName(), supplier, this.getCacheKey(entityNames));
	}

	default Mono<T> cacheValueOrGet(Supplier<Mono<T>> supplier, String... entityNames) {
		return this.getCacheService().cacheValueOrGet(this.getCacheName(), supplier, this.getCacheKey(entityNames));
	}
}
