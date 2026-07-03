package com.risk.dupdetect;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration;
import org.springframework.data.web.config.EnableSpringDataWebSupport;

/**
 * PS-62 Duplicate-Transaction Detection System
 * <p>
 * Redis auto-configuration is excluded so the application starts cleanly
 * in local/H2 mode without a running Redis instance.
 * To enable the Redis-backed sliding window, remove these exclusions and
 * set {@code transaction.store.type=redis} in your environment properties.
 */
@SpringBootApplication(exclude = {
        RedisAutoConfiguration.class,
        RedisRepositoriesAutoConfiguration.class,
        RedisReactiveAutoConfiguration.class
})
@EnableSpringDataWebSupport(pageSerializationMode = EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO)
public class DupdetectApplication {

    public static void main(String[] args) {
        SpringApplication.run(DupdetectApplication.class, args);
    }

}

