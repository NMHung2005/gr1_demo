package com.example.demo.config;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.serializer.SerializationException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronizationUtils;
import org.springframework.cache.transaction.TransactionAwareCacheDecorator;

class FailOpenCacheTests {
    private static final Duration TTL = Duration.ofSeconds(30);

    @Test
    void failedEvictionBypassesOldValuesAndSkipsWritesUntilFullTtlThenRecovers() {
        var clock = new AtomicLong();
        var redis = spy(new ConcurrentMapCache("books"));
        redis.put("all", "old books");
        var cache = new FailOpenCache(redis, TTL, clock::get);
        doThrow(new RedisConnectionFailureException("offline")).when(redis).evict("all");

        cache.evict("all");
        assertThat(cache.get("all")).isNull();
        cache.put("all", "do not extend old value lifetime");
        verify(redis, never()).get("all");
        verify(redis, never()).put("all", "do not extend old value lifetime");

        clock.set(Duration.ofSeconds(29).toNanos());
        assertThat(cache.get("all")).isNull();
        verify(redis, never()).get("all");

        // Model Redis TTL expiry, independently of the wrapper's circuit state.
        redis.getNativeCache().clear();
        clock.set(TTL.toNanos());
        assertThat(cache.get("all")).isNull();
        verify(redis).get("all");
        cache.put("all", "fresh database books");
        assertThat(cache.get("all").get()).isEqualTo("fresh database books");
    }

    @Test
    void continuedOutageStartsAnotherBypassWindow() {
        var clock = new AtomicLong();
        var redis = mock(Cache.class);
        when(redis.getName()).thenReturn("books");
        when(redis.get("all")).thenThrow(new RedisConnectionFailureException("offline"));
        var cache = new FailOpenCache(redis, TTL, clock::get);
        assertThat(cache.get("all")).isNull();
        clock.set(TTL.toNanos());
        assertThat(cache.get("all")).isNull();
        assertThat(cache.get("all")).isNull();
        verify(redis, times(2)).get("all");
    }

    @Test
    void putFailureStillReturnsLoadedValueWithoutRunningLoaderTwice() {
        var redis = spy(new ConcurrentMapCache("books"));
        doThrow(new RedisConnectionFailureException("offline")).when(redis).put("all", "database books");
        var cache = new FailOpenCache(redis, TTL);
        var calls = new AtomicLong();
        assertThat(cache.get("all", () -> { calls.incrementAndGet(); return "database books"; }))
                .isEqualTo("database books");
        assertThat(calls).hasValue(1);
    }

    @Test
    void databaseTimeoutFromLoaderIsPreservedAndLoaderIsNotRetried() {
        var cache = new FailOpenCache(new ConcurrentMapCache("books"), TTL);
        var timeout = new QueryTimeoutException("database query timed out");
        var calls = new AtomicLong();
        assertThatThrownBy(() -> cache.get("all", () -> {
            calls.incrementAndGet();
            throw timeout;
        })).isInstanceOf(Cache.ValueRetrievalException.class).hasCause(timeout);
        assertThat(calls).hasValue(1);
    }

    @Test
    void serializationErrorsRemainVisible() {
        var redis = mock(Cache.class);
        var failure = new SerializationException("invalid JSON");
        doThrow(failure).when(redis).put("all", "books");
        assertThatThrownBy(() -> new FailOpenCache(redis, TTL).put("all", "books"))
                .isSameAs(failure);
    }

    @Test
    void rollbackDoesNotEvictExistingCache() {
        var redis = spy(new ConcurrentMapCache("books"));
        redis.put("all", "unchanged books");
        Cache cache = new TransactionAwareCacheDecorator(new FailOpenCache(redis, TTL));
        TransactionSynchronizationManager.initSynchronization();
        try {
            cache.evict("all");
            TransactionSynchronizationUtils.triggerAfterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
            verify(redis, never()).evict("all");
            assertThat(cache.get("all").get()).isEqualTo("unchanged books");
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }
}
