package com.fincity.saas.commons.service;

import java.util.Collection;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

//TODO: This is primary cache, I need to implement the Redis cache as secondary to keep it distributed. 

@Service
public class CacheService {

	@Autowired
	private CacheManager cacheManager;

	@Value("${spring.application.name}")
	private String appName;

	public Mono<Boolean> evict(String cacheName, String key) {

		return Mono.fromCallable(() -> {
			Cache x = cacheManager.getCache(cacheName);
			if (x == null)
				return true;

			x.evictIfPresent(key);
			return true;
		})
		        .onErrorResume(t -> Mono.just(false));

	}

	public Mono<Boolean> evict(String cacheName, Object... keys) {

		return makeKey(keys).flatMap(e -> this.evict(cacheName, e));
	}

	public Mono<String> makeKey(Object... args) {

		if (args.length == 1)
			return Mono.just(args[0].toString());

		return Flux.fromArray(args)
		        .filter(Objects::nonNull)
		        .map(Object::toString)
		        .collect(Collectors.joining());
	}

	@SuppressWarnings("unchecked")
	public <T> Mono<T> put(String cacheName, Object value, Object... keys) {

		this.makeKey(keys)
		        .subscribe(key -> this.cacheManager.getCache(cacheName)
		                .put(key, value));

		return Mono.just((T) value);
	}

	@SuppressWarnings("unchecked")
	public <T> Mono<T> get(String cacheName, Object... keys) {

		return this.makeKey(keys)
		        .flatMap(key -> Mono.justOrEmpty(this.cacheManager.getCache(cacheName)
		                .get(key))
		                .map(vw -> (T) vw.get()));
	}

	public Mono<Boolean> evictAll(String cacheName) {

		return Mono.fromCallable(() -> {

			this.cacheManager.getCache(cacheName)
			        .clear();
			return true;
		})
		        .onErrorResume(t -> Mono.just(false));
	}

	public Mono<Boolean> evictAllCaches() {
		return Flux.fromIterable(this.cacheManager.getCacheNames())
		        .map(this.cacheManager::getCache)
		        .map(e ->
				{
			        e.clear();
			        return true;
		        })
		        .reduce((a, b) -> a && b);
	}

	public Mono<Collection<String>> getCacheNames() {

		return Mono.just(this.cacheManager.getCacheNames());
	}
}