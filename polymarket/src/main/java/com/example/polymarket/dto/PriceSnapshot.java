package com.example.polymarket.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record PriceSnapshot(
        String assetId,
        String marketSlug,
        String question,
        String outcome,
        String sourceEventType,
        BigDecimal price,
        BigDecimal bestBid,
        BigDecimal bestAsk,
        Instant eventTimestamp
) {}
