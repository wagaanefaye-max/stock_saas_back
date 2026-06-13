package com.stocksaas.security;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserDetailsCacheEvictor {

    private final CacheManager cacheManager;

    public void evict(String email) {
        if (email == null || email.isBlank()) {
            return;
        }
        Cache cache = cacheManager.getCache("userDetails");
        if (cache != null) {
            cache.evict(email.trim().toLowerCase());
        }
    }
}
