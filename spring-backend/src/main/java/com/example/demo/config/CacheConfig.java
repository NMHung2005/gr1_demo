package com.example.demo.config;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import com.example.demo.dto.BookResponse;
import com.example.demo.dto.LoanStatsResponse;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${app.cache.books-ttl:30s}")
    private Duration booksTtl = Duration.ofSeconds(30);

    @Value("${app.cache.statistics-ttl:15s}")
    private Duration statisticsTtl = Duration.ofSeconds(15);

    @Value("${app.cache.key-prefix:library:v2:}")
    private String keyPrefix = "library:v2:";

    @Bean
    public RedisCacheManager cacheManager(
            RedisConnectionFactory connectionFactory) {

        if (booksTtl.isZero() || booksTtl.isNegative() || statisticsTtl.isZero() || statisticsTtl.isNegative()) {
            throw new IllegalArgumentException("Cache TTLs must be positive");
        }
        RedisCacheConfiguration configuration = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(booksTtl)
                .prefixCacheNameWith(keyPrefix)
                .disableCachingNullValues();

        JsonMapper mapper = JsonMapper.builder().build();
        var bookListType = mapper.getTypeFactory()
                .constructCollectionType(List.class, BookResponse.class);
        var serializer = new JacksonJsonRedisSerializer<List<BookResponse>>(mapper, bookListType);
        RedisCacheConfiguration bookConfiguration = configuration
                .serializeValuesWith(SerializationPair.fromSerializer(serializer));
        var statisticsSerializer = new JacksonJsonRedisSerializer<LoanStatsResponse>(mapper, LoanStatsResponse.class);
        RedisCacheConfiguration statisticsConfiguration = configuration.entryTtl(statisticsTtl)
                .serializeValuesWith(SerializationPair.fromSerializer(statisticsSerializer));
        Map<String, Duration> ttlByCache = Map.of("books", booksTtl, "loanStats", statisticsTtl);

        var manager = new RedisCacheManager(
                RedisCacheWriter.create(connectionFactory, writer -> writer.immediateWrites(true)),
                configuration, false, Map.of("books", bookConfiguration, "loanStats", statisticsConfiguration)) {
            @Override
            protected Cache decorateCache(Cache cache) {
                Cache optional = new FailOpenCache(cache, ttlByCache.get(cache.getName()));
                return super.decorateCache(optional);
            }
        };
        manager.setTransactionAware(true);
        return manager;
    }
}
