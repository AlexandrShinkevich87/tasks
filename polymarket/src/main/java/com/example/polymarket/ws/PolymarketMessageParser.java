package com.example.polymarket.ws;

import com.example.polymarket.dto.PriceSnapshot;
import com.example.polymarket.dto.TrackedMarket;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

@Component
public class PolymarketMessageParser {

    private final ObjectMapper objectMapper;

    public PolymarketMessageParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Optional<ParsedWsMessage> parse(String message, Map<String, TrackedMarket> trackedByAssetId) {
        try {
            JsonNode root = objectMapper.readTree(message);
            String eventType = root.path("event_type").asText();

            return switch (eventType) {
                case "best_bid_ask" -> parseBestBidAsk(root, trackedByAssetId);
                case "last_trade_price" -> parseLastTradePrice(root, trackedByAssetId);
                default -> Optional.empty();
            };
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private Optional<ParsedWsMessage> parseBestBidAsk(
            JsonNode root,
            Map<String, TrackedMarket> trackedByAssetId
    ) {
        String assetId = root.path("asset_id").asText();
        TrackedMarket tracked = trackedByAssetId.get(assetId);
        if (tracked == null) {
            return Optional.empty();
        }

        BigDecimal bestBid = decimalOrNull(root.get("best_bid"));
        BigDecimal bestAsk = decimalOrNull(root.get("best_ask"));
        if (bestBid == null || bestAsk == null) {
            return Optional.empty();
        }

        BigDecimal midpoint = bestBid.add(bestAsk)
                .divide(BigDecimal.valueOf(2), 8, RoundingMode.HALF_UP);

        return Optional.of(new ParsedWsMessage(
                assetId,
                new PriceSnapshot(
                        assetId,
                        tracked.marketSlug(),
                        tracked.question(),
                        tracked.outcome(),
                        "best_bid_ask",
                        midpoint,
                        bestBid,
                        bestAsk,
                        epochMillis(root.path("timestamp").asText(null))
                )
        ));
    }

    private Optional<ParsedWsMessage> parseLastTradePrice(
            JsonNode root,
            Map<String, TrackedMarket> trackedByAssetId
    ) {
        String assetId = root.path("asset_id").asText();
        TrackedMarket tracked = trackedByAssetId.get(assetId);
        if (tracked == null) {
            return Optional.empty();
        }

        BigDecimal price = decimalOrNull(root.get("price"));
        if (price == null) {
            return Optional.empty();
        }

        return Optional.of(new ParsedWsMessage(
                assetId,
                new PriceSnapshot(
                        assetId,
                        tracked.marketSlug(),
                        tracked.question(),
                        tracked.outcome(),
                        "last_trade_price",
                        price,
                        null,
                        null,
                        epochMillis(root.path("timestamp").asText(null))
                )
        ));
    }

    private BigDecimal decimalOrNull(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        String value = node.asText();
        if (value == null || value.isBlank()) {
            return null;
        }
        return new BigDecimal(value);
    }

    private Instant epochMillis(String timestamp) {
        if (timestamp == null || timestamp.isBlank()) {
            return null;
        }
        return Instant.ofEpochMilli(Long.parseLong(timestamp));
    }
}