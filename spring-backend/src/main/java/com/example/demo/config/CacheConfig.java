package com.example.demo.config;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import com.example.demo.dto.BookResponse;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.Cache;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import tools.jackson.databind.json.JsonMapper;

@Configuration
@EnableCaching
@Profile("!test")
public class CacheConfig {

    private static final Duration BOOK_TTL = Duration.ofSeconds(30);

    @Bean
    public RedisCacheManager cacheManager(
            RedisConnectionFactory connectionFactory) {

        RedisCacheConfiguration configuration = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(BOOK_TTL)
                // v2 uses typed JSON; do not try to read v1 JDK-serialized values.
                .prefixCacheNameWith("library:v2:")
                .disableCachingNullValues();

        JsonMapper mapper = JsonMapper.builder().build();
        var bookListType = mapper.getTypeFactory()
                .constructCollectionType(List.class, BookResponse.class);
        var serializer = new JacksonJsonRedisSerializer<List<BookResponse>>(mapper, bookListType);
        RedisCacheConfiguration bookConfiguration = configuration
                .serializeValuesWith(SerializationPair.fromSerializer(serializer));

        var manager = new RedisCacheManager(
                RedisCacheWriter.nonLockingRedisCacheWriter(connectionFactory),
                configuration, Map.of("books", bookConfiguration)) {
            @Override
            protected Cache decorateCache(Cache cache) {
                // Order matters: afterCommit must call the fail-open wrapper too.
                Cache optional = cache.getName().equals("books")
                        ? new FailOpenCache(cache, BOOK_TTL) : cache;
                return super.decorateCache(optional);
            }
        };
        manager.setTransactionAware(true);
        return manager;
    }
}
