package com.example.polymarket.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;

@Component
public class PriceStateStore {

    private static final String KEY_PREFIX = "polymarket:last-price:";

    private final StringRedisTemplate redisTemplate;

    public PriceStateStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public Optional<BigDecimal> getLastPrice(String assetId) {
        String value = redisTemplate.opsForValue().get(buildKey(assetId));
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new BigDecimal(value));
    }

    public void saveLastPrice(String assetId, BigDecimal price) {
        redisTemplate.opsForValue().set(buildKey(assetId), price.toPlainString());
    }

    private String buildKey(String assetId) {
        return KEY_PREFIX + assetId;
    }
}