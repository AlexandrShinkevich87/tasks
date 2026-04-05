package com.example.polymarket.service;

import com.example.polymarket.client.GammaApiClient;
import com.example.polymarket.config.PolymarketProperties;
import com.example.polymarket.dto.TrackedMarket;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class MarketResolverService {

    private final PolymarketProperties properties;
    private final GammaApiClient gammaApiClient;
    private final ObjectMapper objectMapper;

    public MarketResolverService(
            PolymarketProperties properties,
            GammaApiClient gammaApiClient,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.gammaApiClient = gammaApiClient;
        this.objectMapper = objectMapper;
    }

    public List<TrackedMarket> resolveTrackedMarkets() {
        return switch (properties.getMarketSelectionMode()) {
            case CONFIG -> resolveConfiguredMarkets();
            case AUTO_TOP_ACTIVE -> resolveAutoTopActive();
        };
    }

    private List<TrackedMarket> resolveConfiguredMarkets() {
        List<TrackedMarket> result = new ArrayList<>();

        for (String slug : properties.getTrackedMarkets()) {
            GammaApiClient.GammaMarketDto market = gammaApiClient.getMarketBySlug(slug);
            List<String> tokenIds = parseTokenIds(market.getClobTokenIds());

            if (tokenIds.size() >= 2) {
                result.add(new TrackedMarket(tokenIds.get(0), market.getSlug(), market.getQuestion(), "YES"));
                result.add(new TrackedMarket(tokenIds.get(1), market.getSlug(), market.getQuestion(), "NO"));
            }
        }

        return result;
    }

    private List<TrackedMarket> resolveAutoTopActive() {
        List<TrackedMarket> result = new ArrayList<>();

        List<GammaApiClient.GammaMarketDto> markets =
                gammaApiClient.getActiveMarkets(properties.getAutoTrackCount());

        for (GammaApiClient.GammaMarketDto market : markets) {
            List<String> tokenIds = parseTokenIds(market.getClobTokenIds());
            if (tokenIds.size() >= 2) {
                result.add(new TrackedMarket(tokenIds.get(0), market.getSlug(), market.getQuestion(), "YES"));
            }
        }
        return result;
    }

    private List<String> parseTokenIds(String jsonArray) {
        try {
            return objectMapper.readValue(jsonArray, new TypeReference<>() {});
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse clobTokenIds: " + jsonArray, e);
        }
    }
}