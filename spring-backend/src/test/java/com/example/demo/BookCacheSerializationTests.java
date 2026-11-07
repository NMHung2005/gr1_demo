package com.example.demo;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.example.demo.config.CacheConfig;
import com.example.demo.config.FailOpenCache;
import com.example.demo.dto.BookResponse;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCache;
import org.springframework.cache.transaction.TransactionAwareCacheDecorator;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;

class BookCacheSerializationTests {
    @SuppressWarnings("unchecked")
    private SerializationPair<Object> codec(RedisCacheManager manager) {
        manager.afterPropertiesSet();
        var cache = manager.getCache("books");
        if (cache instanceof TransactionAwareCacheDecorator decorator) cache = decorator.getTargetCache();
        if (cache instanceof FailOpenCache optional) cache = optional.getTargetCache();
        return (SerializationPair<Object>) ((RedisCache) cache).getCacheConfiguration().getValueSerializationPair();
    }

    private List<BookResponse> books() {
        return new ArrayList<>(List.of(new BookResponse(1L, "Clean Code", "Robert Martin", "Code", 2008, 3, 2)));
    }

    @Test
    void bookCacheUsesReadableJsonAndRetainsTypedElements() {
        var pair = codec(new CacheConfig().cacheManager(new LettuceConnectionFactory()));
        var buffer = pair.write(books());
        byte[] bytes = new byte[buffer.remaining()];
        buffer.duplicate().get(bytes);
        String json = new String(bytes, StandardCharsets.UTF_8);
        assertThat(json).startsWith("[").contains("\"title\":\"Clean Code\"");
        List<?> decoded = (List<?>) pair.read(ByteBuffer.wrap(bytes));
        assertThat(decoded.get(0)).isInstanceOf(BookResponse.class);
        assertThat(((BookResponse) decoded.get(0)).available()).isEqualTo(2);
    }

    @Test
    void cacheReadAfterApplicationRestartUsesTheCurrentDtoClassloader() throws Exception {
        var oldPair = codec(new CacheConfig().cacheManager(new LettuceConnectionFactory()));
        ByteBuffer data = oldPair.write(books());

        // Simulate DevTools: the new application loads DTO/config again, while Redis
        // and Spring libraries remain in their original (parent) classloader.
        ClassLoader restarted = new ClassLoader(getClass().getClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (!name.equals(BookResponse.class.getName()) && !name.equals(CacheConfig.class.getName())
                        && !name.startsWith(CacheConfig.class.getName() + "$")) {
                    return super.loadClass(name, resolve);
                }
                synchronized (getClassLoadingLock(name)) {
                    Class<?> loaded = findLoadedClass(name);
                    if (loaded == null) {
                        String resource = name.replace('.', '/') + ".class";
                        try (var input = getParent().getResourceAsStream(resource)) {
                            if (input == null) throw new ClassNotFoundException(name);
                            byte[] bytes = input.readAllBytes();
                            loaded = defineClass(name, bytes, 0, bytes.length);
                        } catch (IOException ex) { throw new ClassNotFoundException(name, ex); }
                    }
                    if (resolve) resolveClass(loaded);
                    return loaded;
                }
            }
        };

        Class<?> currentConfig = restarted.loadClass(CacheConfig.class.getName());
        Object config = currentConfig.getConstructor().newInstance();
        RedisCacheManager manager = (RedisCacheManager) currentConfig
                .getMethod("cacheManager", RedisConnectionFactory.class)
                .invoke(config, new LettuceConnectionFactory());
        List<?> decoded = (List<?>) codec(manager).read(data);
        Class<?> currentDto = restarted.loadClass(BookResponse.class.getName());

        assertThat(decoded.get(0).getClass()).isEqualTo(currentDto);
        assertThat(decoded.get(0).getClass().getClassLoader()).isSameAs(restarted);
        currentDto.cast(decoded.get(0));
    }
}
