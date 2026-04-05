package com.example.polymarket.dto;

public record TrackedMarket(
        String assetId,
        String marketSlug,
        String question,
        String outcome
) {
}
