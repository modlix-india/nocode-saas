package com.fincity.saas.commons.service;

import java.util.Collection;
import java.util.Objects;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import io.lettuce.core.api.async.RedisAsyncCommands;
import io.lettuce.core.pubsub.RedisPubSubAdapter;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import io.lettuce.core.pubsub.api.async.RedisPubSubAsyncCommands;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class CacheService extends RedisPubSubAdapter<String, String> {

	@Autowired
	private CacheManager cacheManager;

	@Autowired(required = false)
	private RedisAsyncCommands<String, Object> redisAsyncCommand;

	@Autowired(required = false)
	@Qualifier("subRedisAsyncCommand")
	private RedisPubSubAsyncCommands<String, String> subAsyncCommand;

	@Autowired(required = false)
	@Qualifier("pubRedisAsyncCommand")
	private RedisPubSubAsyncCommands<String, String> pubAsyncCommand;

	@Autowired(required = false)
	private StatefulRedisPubSubConnection<String, String> subConnect;

	@Value("${spring.application.name}")
	private String appName;

	@Value("${redis.channel:evictionChannel}")
	private String channel;

	@Value("${redis.cache.prefix:unk}")
	private String redisPrefix;

	@PostConstruct
	public void registerEviction() {

		if (redisAsyncCommand == null)
			return;

		subAsyncCommand.subscribe(channel);
		subConnect.addListener(this);
	}

	public Mono<Boolean> evict(String cName, String key) {

		String cacheName = this.redisPrefix + "-" + cName;

		if (pubAsyncCommand != null) {
			Mono.fromCompletionStage(pubAsyncCommand.publish(this.channel, cacheName + ":" + key))
			        .map(e -> true)
			        .subscribe();

			return Mono.fromCompletionStage(redisAsyncCommand.hdel(cacheName, key))
			        .map(e -> true);
		}

		return Mono.fromCallable(() -> this.caffineCacheEvict(cacheName, key))
		        .onErrorResume(t -> Mono.just(false));

	}

	private Boolean caffineCacheEvict(String cacheName, String key) {

		Cache x = cacheManager.getCache(cacheName);
		if (x != null)
			x.evictIfPresent(key);
		return true;
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
	public <T> Mono<T> put(String cName, Object value, Object... keys) {

		String cacheName = this.redisPrefix + "-" + cName;

		this.makeKey(keys)
		        .flatMap(key ->
				{

			        this.cacheManager.getCache(cacheName)
			                .put(key, value);

			        if (redisAsyncCommand == null)
				        return Mono.just(true);

			        Mono.fromCompletionStage(redisAsyncCommand.hset(cacheName, key, value))
			                .subscribe();

			        return Mono.just(true);
		        })
		        .subscribe();

		return Mono.just((T) value);
	}

	@SuppressWarnings("unchecked")
	public <T> Mono<T> get(String cName, Object... keys) {

		String cacheName = this.redisPrefix + "-" + cName;

		return this.makeKey(keys)
		        .flatMap(key ->
				{

			        Mono<T> value = Mono.justOrEmpty(this.cacheManager.getCache(cacheName)
			                .get(key))
			                .map(vw -> (T) vw.get());

			        if (redisAsyncCommand == null)
				        return value;

			        return value.switchIfEmpty(
			                Mono.defer(() -> Mono.fromCompletionStage(redisAsyncCommand.hget(cacheName, key))
			                		.map(v -> {
			        					
			        					System.err.println(cacheName + " -- " + key +" -- "+v);
			        					
			        					return v;
			        				})
			                        .map(vw -> (T) vw)));
		        });
	}

	public Mono<Boolean> evictAll(String cName) {

		String cacheName = this.redisPrefix + "-" + cName;

		if (pubAsyncCommand != null) {
			Mono.fromCompletionStage(pubAsyncCommand.publish(this.channel, cacheName + ":*"))
			        .subscribe();

			return Mono.fromCompletionStage(redisAsyncCommand.del(cacheName))
			        .map(e -> true)
			        .defaultIfEmpty(true);
		}

		return Mono.fromCallable(() -> {

			this.cacheManager.getCache(cacheName)
			        .clear();
			return true;
		})
		        .onErrorResume(t -> Mono.just(false));
	}

	public Mono<Boolean> evictAllCaches() {

		if (pubAsyncCommand != null) {

			return Mono.fromCompletionStage(redisAsyncCommand.keys(this.redisPrefix + "-*"))
			        .flatMapMany(Flux::fromIterable)
			        .map(e ->
					{

				        Mono.fromCompletionStage(pubAsyncCommand.publish(this.channel, e + ":*"))
				                .subscribe();
				        return Mono.fromCompletionStage(redisAsyncCommand.del(e));

			        })
			        .map(e -> true)
			        .reduce((a, b) -> a && b);
		}

		Flux<String> flux = Flux.fromIterable(this.cacheManager.getCacheNames());

		return flux.map(this.cacheManager::getCache)
		        .map(e ->
				{
			        e.clear();
			        return true;
		        })
		        .reduce((a, b) -> a && b);
	}

	public Mono<Collection<String>> getCacheNames() {

		return Mono.just(this.cacheManager.getCacheNames()
		        .stream()
		        .map(e -> e.substring(this.redisPrefix.length() + 1))
		        .toList());
	}

	@Override
	public void message(String channel, String message) {

		if (channel == null || !channel.equals(this.channel))
			return;

		int colon = message.indexOf(':');
		if (colon == -1)
			return;

		String cacheName = message.substring(0, colon);
		String cacheKey = message.substring(colon + 1);

		Cache cache = this.cacheManager.getCache(cacheName);

		if (cache == null)
			return;

		if (cacheKey.equals("*"))
			cache.clear();
		else
			cache.evictIfPresent(cacheKey);
	}
}
