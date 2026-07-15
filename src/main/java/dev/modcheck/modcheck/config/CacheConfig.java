package dev.modcheck.modcheck.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    RedisCacheConfiguration cacheConfiguration() {
        var serializer = GenericJacksonJsonRedisSerializer.builder()
            .enableSpringCacheNullValueSupport()
            .enableUnsafeDefaultTyping()
            .build();

        return RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofHours(6))
            .serializeValuesWith(RedisSerializationContext.SerializationPair
                .fromSerializer(serializer));
    }
}