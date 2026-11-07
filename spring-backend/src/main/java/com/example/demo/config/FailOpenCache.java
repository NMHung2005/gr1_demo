package com.example.demo.config;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;

/**
 * Optional, synchronous read-model cache. Business/database failures are never
 * swallowed.
 */
public final class FailOpenCache implements Cache {
    private static final Logger log = LoggerFactory.getLogger(FailOpenCache.class);
    private final Cache target;
    private final long bypassNanos;
    private final LongSupplier ticker;
    private volatile long failedAt;
    private volatile boolean failed;

    public FailOpenCache(Cache target, Duration ttl) {
        this(target, ttl, System::nanoTime);
    }

    FailOpenCache(Cache target, Duration ttl, LongSupplier ticker) {
        if (ttl.isZero() || ttl.isNegative())
            throw new IllegalArgumentException("Cache TTL must be positive");
        this.target = target;
        this.bypassNanos = ttl.toNanos();
        this.ticker = ticker;
    }

    public Cache getTargetCache() {
        return target;
    }

    @Override
    public String getName() {
        return target.getName();
    }

    @Override
    public Object getNativeCache() {
        return target.getNativeCache();
    }

    private boolean bypassing() {
        return failed && ticker.getAsLong() - failedAt < bypassNanos;
    }

    private synchronized void unavailable(String operation, RuntimeException failure) {
        if (!bypassing()) {
            // Do not log keys/values, which may contain user data.
            log.warn("Cache {} unavailable during {}; bypassing for {} ms ({})",
                    getName(), operation, Duration.ofNanos(bypassNanos).toMillis(),
                    failure.getClass().getSimpleName());
        }
        failedAt = ticker.getAsLong();
        failed = true;
    }

    private <T> T attempt(String operation, Supplier<T> action, T fallback) {
        if (bypassing())
            return fallback;
        try {
            return action.get();
        } catch (RedisConnectionFailureException | QueryTimeoutException failure) {
            unavailable(operation, failure);
            return fallback;
        }
    }

    @Override
    public ValueWrapper get(Object key) {
        return attempt("get", () -> target.get(key), null);
    }

    @Override
    public <T> T get(Object key, Class<T> type) {
        return attempt("get", () -> target.get(key, type), null);
    }

    @Override
    public synchronized <T> T get(Object key, Callable<T> loader) {
        ValueWrapper cached = get(key);
        if (cached != null) {
            @SuppressWarnings("unchecked")
            T value = (T) cached.get();
            return value;
        }
        T value;
        try {
            // Outside the Redis failure handler: never retry or hide loader failures.
            value = loader.call();
        } catch (Exception failure) {
            throw new ValueRetrievalException(key, loader, failure);
        }
        put(key, value);
        return value;
    }

    @Override
    public void put(Object key, Object value) {
        attempt("put", () -> {
            target.put(key, value);
            return null;
        }, null);
    }

    @Override
    public ValueWrapper putIfAbsent(Object key, Object value) {
        return attempt("putIfAbsent", () -> target.putIfAbsent(key, value), null);
    }

    @Override
    public void evict(Object key) {
        attempt("evict", () -> {
            target.evict(key);
            return null;
        }, null);
    }

    @Override
    public boolean evictIfPresent(Object key) {
        return attempt("evictIfPresent", () -> target.evictIfPresent(key), false);
    }

    @Override
    public void clear() {
        attempt("clear", () -> {
            target.clear();
            return null;
        }, null);
    }

    @Override
    public boolean invalidate() {
        return attempt("invalidate", target::invalidate, false);
    }
}
