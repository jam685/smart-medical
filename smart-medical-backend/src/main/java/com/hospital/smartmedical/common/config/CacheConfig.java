package com.hospital.smartmedical.common.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
public class CacheConfig {
    @Value("${caffeine.expire-minutes:30}")
    private int expireMinutes;
    @Value("${caffeine.max-size:1000}")
    private int maxSize;

    @Bean
    public Cache<String, Object> localCache() {
        return Caffeine.newBuilder()
                .expireAfterWrite(expireMinutes, TimeUnit.MINUTES)
                .maximumSize(maxSize)
                .build();
    }
}
