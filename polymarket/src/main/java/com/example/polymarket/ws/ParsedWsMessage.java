package com.example.polymarket.ws;

import com.example.polymarket.dto.PriceSnapshot;

public record ParsedWsMessage(
        String assetId,
        PriceSnapshot snapshot
) {
}
