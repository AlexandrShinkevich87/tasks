package com.example.polymarket.ws;

import com.example.polymarket.config.PolymarketProperties;
import com.example.polymarket.dto.TrackedMarket;
import com.example.polymarket.service.MarketResolverService;
import com.example.polymarket.service.PriceUpdateService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class PolymarketWebSocketClient {

    private final ObjectMapper objectMapper;
    private final MarketResolverService marketResolverService;
    private final PriceUpdateService priceUpdateService;
    private final PolymarketProperties properties;
    private final PolymarketMessageParser messageParser;

    private final Map<String, TrackedMarket> trackedByAssetId = new ConcurrentHashMap<>();
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    private volatile WebSocket webSocket;
    private volatile boolean shuttingDown = false;
    private volatile List<String> assetIds = List.of();

    @PostConstruct
    public void start() {
        List<TrackedMarket> trackedMarkets = marketResolverService.resolveTrackedMarkets();

        trackedByAssetId.clear();
        trackedMarkets.forEach(tm -> trackedByAssetId.put(tm.assetId(), tm));

        this.assetIds = trackedMarkets.stream()
                .map(TrackedMarket::assetId)
                .distinct()
                .toList();

        log.info("Preparing websocket subscription for {} tracked markets and {} asset ids: {}",
                trackedMarkets.size(), assetIds.size(), assetIds);

        if (assetIds.isEmpty()) {
            log.warn("No asset ids resolved. WebSocket connection will not be started");
            return;
        }

        connect();
    }

    private void connect() {
        if (shuttingDown) {
            return;
        }

        log.info("Connecting to Polymarket WebSocket: {}", properties.getWsUrl());

        httpClient.newWebSocketBuilder()
                .buildAsync(URI.create(properties.getWsUrl()), new Listener(assetIds))
                .whenComplete((socket, error) -> {
                    if (error != null) {
                        log.error("Failed to establish WebSocket connection", error);
                        scheduleReconnect("connect failure");
                        return;
                    }

                    this.webSocket = socket;
                    log.info("WebSocket connection established");
                });
    }

    private void scheduleReconnect(String reason) {
        if (shuttingDown) {
            return;
        }

        long delayMs = properties.getReconnectDelay().toMillis();
        log.warn("Scheduling reconnect in {} ms due to {}", delayMs, reason);

        scheduler.schedule(this::connect, delayMs, TimeUnit.MILLISECONDS);
    }

    @PreDestroy
    public void shutdown() {
        shuttingDown = true;
        log.info("Shutting down Polymarket WebSocket client");

        WebSocket current = this.webSocket;
        if (current != null) {
            try {
                current.sendClose(WebSocket.NORMAL_CLOSURE, "Application shutdown")
                        .toCompletableFuture()
                        .get(5, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.warn("Failed to close websocket gracefully", e);
                current.abort();
            }
        }

        scheduler.shutdownNow();
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

                log.info("Subscribed to {} asset ids: {}", assetIds.size(), assetIds);
                WebSocket.Listener.super.onOpen(webSocket);
            } catch (Exception e) {
                log.error("Failed to subscribe", e);
                scheduleReconnect("subscription failure");
            }
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            buffer.append(data);
            if (last) {
                String message = buffer.toString();
                buffer.setLength(0);

                messageParser.parse(message, trackedByAssetId)
                        .ifPresent(parsed -> priceUpdateService.handlePriceUpdate(parsed.snapshot()));
            }

            return WebSocket.Listener.super.onText(webSocket, data, last);
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            log.warn("WebSocket closed. statusCode={}, reason={}", statusCode, reason);
            scheduleReconnect("close");
            return WebSocket.Listener.super.onClose(webSocket, statusCode, reason);
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            log.error("WebSocket error", error);
            scheduleReconnect("error");
            WebSocket.Listener.super.onError(webSocket, error);
        }
    }
}