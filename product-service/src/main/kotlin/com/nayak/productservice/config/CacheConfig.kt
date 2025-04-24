package com.nayak.productservice.config


import com.github.benmanes.caffeine.cache.Caffeine
import org.springframework.cache.CacheManager
import org.springframework.cache.caffeine.CaffeineCacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.concurrent.TimeUnit

@Configuration
class CacheConfig {

    @Bean
    fun cacheManager(): CacheManager {
        val caffeineCacheManager = CaffeineCacheManager("products", "categories") // Define cache names
        caffeineCacheManager.setCaffeine(
            Caffeine.newBuilder()
                .initialCapacity(100)
                .maximumSize(500)
                .expireAfterWrite(10, TimeUnit.MINUTES) // Cache entries expire after 10 minutes
                .recordStats() // for monitoring cache performance via Actuator
        )

        return caffeineCacheManager
    }
}