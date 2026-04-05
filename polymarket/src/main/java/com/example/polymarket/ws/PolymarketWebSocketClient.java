package com.example.polymarket.ws;

import com.example.polymarket.dto.PriceSnapshot;
import com.example.polymarket.dto.TrackedMarket;
import com.example.polymarket.service.MarketResolverService;
import com.example.polymarket.service.PriceUpdateService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;

@Component
public class PolymarketWebSocketClient {

    private static final Logger log = LoggerFactory.getLogger(PolymarketWebSocketClient.class);

    private final ObjectMapper objectMapper;
    private final MarketResolverService marketResolverService;
    private final PriceUpdateService priceUpdateService;
    private final String wsUrl;

    private final Map<String, TrackedMarket> trackedByAssetId = new HashMap<>();

    public PolymarketWebSocketClient(
            ObjectMapper objectMapper,
            MarketResolverService marketResolverService,
            PriceUpdateService priceUpdateService,
            @Value("${polymarket.ws-url}") String wsUrl
    ) {
        this.objectMapper = objectMapper;
        this.marketResolverService = marketResolverService;
        this.priceUpdateService = priceUpdateService;
        this.wsUrl = wsUrl;
    }

    @PostConstruct
    public void start() {
        List<TrackedMarket> trackedMarkets = marketResolverService.resolveTrackedMarkets();
        trackedMarkets.forEach(tm -> trackedByAssetId.put(tm.assetId(), tm));

        List<String> assetIds = trackedMarkets.stream()
                .map(TrackedMarket::assetId)
                .distinct()
                .toList();

        HttpClient.newHttpClient()
                .newWebSocketBuilder()
                .buildAsync(URI.create(wsUrl), new Listener(assetIds));
    }

    private class Listener implements WebSocket.Listener {
        private final StringBuilder buffer = new StringBuilder();
        private final List<String> assetIds;

        private Listener(List<String> assetIds) {
            this.assetIds = assetIds;
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            try {
                Map<String, Object> payload = new HashMap<>();
                payload.put("assets_ids", assetIds);
                payload.put("type", "market");
                payload.put("custom_feature_enabled", true);

                String json = objectMapper.writeValueAsString(payload);
                webSocket.sendText(json, true);
                log.info("Subscribed to {} assets", assetIds.size());
                WebSocket.Listener.super.onOpen(webSocket);
            } catch (Exception e) {
                log.error("Failed to subscribe", e);
            }
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            buffer.append(data);
            if (last) {
                String message = buffer.toString();
                buffer.setLength(0);
                handleMessage(message);
            }
            return WebSocket.Listener.super.onText(webSocket, data, last);
        }

        private void handleMessage(String message) {
            try {
                JsonNode root = objectMapper.readTree(message);
                String eventType = root.path("event_type").asText();

                if ("best_bid_ask".equals(eventType)) {
                    handleBestBidAsk(root);
                } else if ("last_trade_price".equals(eventType)) {
                    handleLastTradePrice(root);
                }
            } catch (Exception e) {
                log.warn("Failed to parse ws message: {}", message, e);
            }
        }

        private void handleBestBidAsk(JsonNode root) {
            String assetId = root.path("asset_id").asText();
            TrackedMarket tracked = trackedByAssetId.get(assetId);
            if (tracked == null) {
                return;
            }

            BigDecimal bestBid = decimalOrNull(root.get("best_bid"));
            BigDecimal bestAsk = decimalOrNull(root.get("best_ask"));

            if (bestBid == null || bestAsk == null) {
                return;
            }

            BigDecimal midpoint = bestBid.add(bestAsk)
                    .divide(BigDecimal.valueOf(2), 8, java.math.RoundingMode.HALF_UP);

            PriceSnapshot snapshot = new PriceSnapshot(
                    assetId,
                    tracked.marketSlug(),
                    tracked.question(),
                    tracked.outcome(),
                    "best_bid_ask",
                    midpoint,
                    bestBid,
                    bestAsk,
                    epochMillis(root.path("timestamp").asText(null))
            );

            priceUpdateService.handlePriceUpdate(snapshot);
        }

        private void handleLastTradePrice(JsonNode root) {
            String assetId = root.path("asset_id").asText();
            TrackedMarket tracked = trackedByAssetId.get(assetId);
            if (tracked == null) {
                return;
            }

            BigDecimal price = decimalOrNull(root.get("price"));
            if (price == null) {
                return;
            }

            PriceSnapshot snapshot = new PriceSnapshot(
                    assetId,
                    tracked.marketSlug(),
                    tracked.question(),
                    tracked.outcome(),
                    "last_trade_price",
                    price,
                    null,
                    null,
                    epochMillis(root.path("timestamp").asText(null))
            );

            priceUpdateService.handlePriceUpdate(snapshot);
        }

        private BigDecimal decimalOrNull(JsonNode node) {
            if (node == null || node.isNull() || node.asText().isBlank()) {
                return null;
            }
            return new BigDecimal(node.asText());
        }

        private Instant epochMillis(String timestamp) {
            if (timestamp == null || timestamp.isBlank()) {
                return null;
            }
            return Instant.ofEpochMilli(Long.parseLong(timestamp));
        }
    }
}