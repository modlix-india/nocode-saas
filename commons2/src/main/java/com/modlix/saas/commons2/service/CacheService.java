package com.modlix.saas.commons2.service;

import io.lettuce.core.api.async.RedisAsyncCommands;
import io.lettuce.core.pubsub.RedisPubSubAdapter;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import io.lettuce.core.pubsub.api.async.RedisPubSubAsyncCommands;
import jakarta.annotation.PostConstruct;
import java.util.Collection;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.cache.CacheType;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

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

	@Value("${spring.cache.type:}")
	private CacheType cacheType;

	@PostConstruct
	public void registerEviction() {

		if (redisAsyncCommand == null || this.cacheType == CacheType.NONE)
			return;

		subAsyncCommand.subscribe(channel);
		subConnect.addListener(this);
	}

	public boolean evict(String cName, String key) {

		if (this.cacheType == CacheType.NONE)
			return true;

		String cacheName = this.redisPrefix + "-" + cName;

		if (pubAsyncCommand != null) {
			try {
				pubAsyncCommand.publish(this.channel, cacheName + ":" + key).get();
				redisAsyncCommand.hdel(cacheName, key).get();
				return true;
			} catch (Exception e) {
				return false;
			}
		}

		try {
			return this.caffineCacheEvict(cacheName, key);
		} catch (Exception e) {
			return false;
		}

	}

	private Boolean caffineCacheEvict(String cacheName, String key) {

		Cache x = cacheManager.getCache(cacheName);
		if (x != null)
			x.evictIfPresent(key);
		return true;
	}

	public boolean evict(String cacheName, Object... keys) {

		if (this.cacheType == CacheType.NONE)
			return true;

		String key = makeKey(keys);
		return this.evict(cacheName, key);
	}

	public String makeKey(Object... args) {

		if (args.length == 1)
			return args[0].toString();

		return java.util.Arrays.stream(args)
				.filter(Objects::nonNull)
				.map(Object::toString)
				.collect(Collectors.joining());
	}

	public <T> T put(String cName, T value, Object... keys) {

		if (this.cacheType == CacheType.NONE)
			return value;

		String cacheName = this.redisPrefix + "-" + cName;
		String key = this.makeKey(keys);

		CacheObject co = new CacheObject((Object) value);

		this.cacheManager.getCache(cacheName)
				.put(key, co);

		if (redisAsyncCommand != null) {
			try {
				redisAsyncCommand.hset(cacheName, key, co).get();
			} catch (Exception e) {
				// Log error but continue
			}
		}

		return value;
	}

	@SuppressWarnings("unchecked")
	public <T> T get(String cName, Object... keys) {

		if (this.cacheType == CacheType.NONE)
			return null;

		String cacheName = this.redisPrefix + "-" + cName;
		String key = this.makeKey(keys);

		CacheObject value = this.cacheManager.getCache(cacheName)
				.get(key, CacheObject.class);

		if (value == null && redisAsyncCommand != null) {
			try {
				value = (CacheObject) redisAsyncCommand.hget(cacheName, key).get();
			} catch (Exception e) {
				// Log error but continue
			}
		}

		return value != null ? (T) value.getObject() : null;
	}

	@SuppressWarnings("unchecked")
	public <T> T cacheValueOrGet(String cName, Supplier<T> supplier, Object... keys) {

		String key = this.makeKey(keys);
		T value = this.get(cName, key);

		if (value == null) {
			value = supplier.get();
			if (value != null) {
				this.put(cName, value, key);
			}
		}

		return value;
	}

	@SuppressWarnings("unchecked")
	public <T> T cacheEmptyValueOrGet(String cName, Supplier<T> supplier, Object... keys) {

		String key = this.makeKey(keys);
		String cacheName = this.redisPrefix + "-" + cName;
		CacheObject cachedValue = this.cacheManager.getCache(cacheName)
				.get(key, CacheObject.class);

		if (cachedValue == null) {
			T value = supplier.get();
			if (value != null) {
				this.put(cName, value, key);
				return value;
			} else {
				this.put(cName, (T) null, key);
				return null;
			}
		}

		return (T) cachedValue.getObject();
	}

	public boolean evictAll(String cName) {

		if (this.cacheType == CacheType.NONE)
			return true;

		String cacheName = this.redisPrefix + "-" + cName;

		if (pubAsyncCommand != null) {
			try {
				pubAsyncCommand.publish(this.channel, cacheName + ":*").get();
				redisAsyncCommand.del(cacheName).get();
				return true;
			} catch (Exception e) {
				return false;
			}
		}

		try {
			this.cacheManager.getCache(cacheName)
					.clear();
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	public boolean evictAllCaches() {

		if (this.cacheType == CacheType.NONE)
			return true;

		if (pubAsyncCommand != null) {
			try {
				Collection<String> keys = redisAsyncCommand.keys(this.redisPrefix + "-*").get();
				for (String key : keys) {
					pubAsyncCommand.publish(this.channel, key + ":*").get();
					redisAsyncCommand.del(key).get();
				}
				return true;
			} catch (Exception e) {
				return false;
			}
		}

		try {
			for (String cacheName : this.cacheManager.getCacheNames()) {
				Cache cache = this.cacheManager.getCache(cacheName);
				if (cache != null) {
					cache.clear();
				}
			}
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	public Collection<String> getCacheNames() {

		return this.cacheManager.getCacheNames()
				.stream()
				.map(e -> e.substring(this.redisPrefix.length() + 1))
				.toList();
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

	public <T> Function<T, T> evictAllFunction(String cacheName) {

		return v -> {
			this.evictAll(cacheName);
			return v;
		};
	}

	public <T> Function<T, T> evictFunction(String cacheName, Object... keys) {
		return v -> {
			this.evict(cacheName, keys);
			return v;
		};
	}

	@SuppressWarnings("unchecked")
	public <T> Function<T, T> evictFunctionWithSuppliers(String cacheName, Supplier<Object>... keySuppliers) {

		Object[] keys = new Object[keySuppliers.length];

		for (int i = 0; i < keySuppliers.length; i++)
			keys[i] = keySuppliers[i].get();

		return v -> {
			this.evict(cacheName, keys);
			return v;
		};
	}

	public <T> Function<T, T> evictFunctionWithKeyFunction(String cacheName,
			Function<T, String> keyMakingFunction) {
		return v -> {
			this.evict(cacheName, keyMakingFunction.apply(v));
			return v;
		};
	}
}
