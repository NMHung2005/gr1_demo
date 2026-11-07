package com.example.demo;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.concurrent.atomic.AtomicInteger;

import com.example.demo.config.CacheConfig;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.AnnotationCacheOperationSource;
import org.springframework.cache.interceptor.CacheInterceptor;
import org.springframework.cache.interceptor.SimpleCacheResolver;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronizationUtils;

class CacheOutageTests {
    private RedisCacheManager manager(RuntimeException failure) {
        var factory = mock(RedisConnectionFactory.class);
        when(factory.getConnection()).thenThrow(failure);
        var manager = new CacheConfig().cacheManager(factory);
        manager.afterPropertiesSet();
        return manager;
    }

    public static class BookRead {
        final AtomicInteger databaseCalls = new AtomicInteger();
        RuntimeException databaseFailure;

        @Cacheable(cacheNames = "books", key = "'all'")
        public String read() {
            databaseCalls.incrementAndGet();
            if (databaseFailure != null) throw databaseFailure;
            return "database books";
        }
    }

    private BookRead proxy(BookRead target, RedisCacheManager manager) {
        var interceptor = new CacheInterceptor();
        interceptor.setCacheOperationSources(new AnnotationCacheOperationSource());
        interceptor.setCacheResolver(new SimpleCacheResolver(manager));
        interceptor.afterPropertiesSet();
        interceptor.afterSingletonsInstantiated();
        var proxy = new ProxyFactory(target);
        proxy.addAdvice(interceptor);
        return (BookRead) proxy.getProxy();
    }

    @Test
    void connectionFailureFallsBackThroughCacheAnnotationToDatabase() {
        var target = new BookRead();
        var service = proxy(target, manager(new RedisConnectionFailureException("unavailable")));
        assertThat(service.read()).isEqualTo("database books");
        assertThat(service.read()).isEqualTo("database books");
        assertThat(target.databaseCalls).hasValue(2);
    }

    @Test
    void timeoutIsAlsoAnOptionalCacheFailure() {
        Cache cache = manager(new QueryTimeoutException("timed out")).getCache("books");
        assertThat(cache.get("all")).isNull();
    }

    @Test
    void deferredEvictionFailureDoesNotEscapeAfterCommit() {
        Cache cache = manager(new RedisConnectionFailureException("unavailable")).getCache("books");
        TransactionSynchronizationManager.initSynchronization();
        try {
            cache.evict("all");
            assertThatCode(TransactionSynchronizationUtils::triggerAfterCommit).doesNotThrowAnyException();
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void deferredPutFailureDoesNotEscapeAfterCommit() {
        Cache cache = manager(new RedisConnectionFailureException("unavailable")).getCache("books");
        TransactionSynchronizationManager.initSynchronization();
        try {
            cache.put("all", java.util.List.of());
            assertThatCode(TransactionSynchronizationUtils::triggerAfterCommit).doesNotThrowAnyException();
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void databaseFailureIsNotHiddenByCacheFallback() {
        var target = new BookRead();
        var failure = new IllegalStateException("database unavailable");
        target.databaseFailure = failure;
        var service = proxy(target, manager(new RedisConnectionFailureException("unavailable")));
        assertThatThrownBy(service::read).isSameAs(failure);
    }

    @Test
    void nonConnectivityFailureIsNotSilentlyIgnored() {
        var failure = new IllegalStateException("invalid cache configuration");
        Cache cache = manager(failure).getCache("books");
        assertThatThrownBy(() -> cache.get("all")).isSameAs(failure);
    }
}
