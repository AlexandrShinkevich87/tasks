package com.example.polymarket.service;

import com.example.polymarket.client.GammaApiClient;
import com.example.polymarket.config.PolymarketProperties;
import com.example.polymarket.dto.TrackedMarket;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MarketResolverService {

    private final PolymarketProperties properties;
    private final GammaApiClient gammaApiClient;
    private final ObjectMapper objectMapper;

    public List<TrackedMarket> resolveTrackedMarkets() {
        List<TrackedMarket> markets = switch (properties.getMarketSelectionMode()) {
            case CONFIG -> resolveConfiguredMarkets();
            case AUTO_TOP_ACTIVE -> resolveAutoTopActive();
        };

        log.info("Resolved {} tracked market assets", markets.size());
        return markets;
    }

    private List<TrackedMarket> resolveConfiguredMarkets() {
        List<TrackedMarket> result = new ArrayList<>();

        for (String slug : properties.getTrackedMarkets()) {
            try {
                GammaApiClient.GammaMarketDto market = gammaApiClient.getMarketBySlug(slug);
                Optional<List<String>> tokenIdsOpt = parseTokenIdsSafely(market.getClobTokenIds(), slug);

                if (tokenIdsOpt.isEmpty()) {
                    continue;
                }

                List<String> tokenIds = tokenIdsOpt.get();
                if (tokenIds.size() >= 2) {
                    result.add(new TrackedMarket(tokenIds.get(0), market.getSlug(), market.getQuestion(), "YES"));
                    result.add(new TrackedMarket(tokenIds.get(1), market.getSlug(), market.getQuestion(), "NO"));
                } else {
                    log.warn("Market '{}' skipped: expected at least 2 token ids, got {}", slug, tokenIds.size());
                }
            } catch (Exception e) {
                log.warn("Market '{}' skipped due to resolution error", slug, e);
            }
        }

        return result;
    }

    private List<TrackedMarket> resolveAutoTopActive() {
        List<TrackedMarket> result = new ArrayList<>();
        List<GammaApiClient.GammaMarketDto> markets = gammaApiClient.getActiveMarkets(properties.getAutoTrackCount());

        for (GammaApiClient.GammaMarketDto market : markets) {
            try {
                Optional<List<String>> tokenIdsOpt = parseTokenIdsSafely(market.getClobTokenIds(), market.getSlug());

                if (tokenIdsOpt.isEmpty()) {
                    continue;
                }

                List<String> tokenIds = tokenIdsOpt.get();
                if (!tokenIds.isEmpty()) {
                    result.add(new TrackedMarket(tokenIds.get(0), market.getSlug(), market.getQuestion(), "YES"));
                } else {
                    log.warn("Market '{}' skipped: no token ids", market.getSlug());
                }
            } catch (Exception e) {
                log.warn("Market '{}' skipped due to resolution error", market.getSlug(), e);
            }
        }

        return result;
    }

    private Optional<List<String>> parseTokenIdsSafely(String jsonArray, String marketKey) {
        try {
            List<String> tokenIds = objectMapper.readValue(jsonArray, new TypeReference<>() {
            });
            return Optional.of(tokenIds);
        } catch (Exception e) {
            log.warn("Failed to parse clobTokenIds for market '{}': {}", marketKey, jsonArray, e);
            return Optional.empty();
        }
    }
}