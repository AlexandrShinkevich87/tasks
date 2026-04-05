package com.example.polymarket.service;

import com.example.polymarket.client.GammaApiClient;
import com.example.polymarket.config.PolymarketProperties;
import com.example.polymarket.dto.TrackedMarket;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketResolverServiceTest {

    private final GammaApiClient gammaApiClient = mock(GammaApiClient.class);
    private final PolymarketProperties properties = new PolymarketProperties();
    private MarketResolverService service;

    @BeforeEach
    void setUp() {
        service = new MarketResolverService(properties, gammaApiClient, new ObjectMapper());
    }

    @Test
    void shouldResolveConfiguredMarkets() {
        properties.setMarketSelectionMode(PolymarketProperties.SelectionMode.CONFIG);
        properties.setTrackedMarkets(List.of("market-1"));

        GammaApiClient.GammaMarketDto dto = market("market-1", "Question 1", "[\"yes-token\",\"no-token\"]");
        when(gammaApiClient.getMarketBySlug("market-1")).thenReturn(dto);

        List<TrackedMarket> result = service.resolveTrackedMarkets();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).assetId()).isEqualTo("yes-token");
        assertThat(result.get(0).outcome()).isEqualTo("YES");
        assertThat(result.get(1).assetId()).isEqualTo("no-token");
        assertThat(result.get(1).outcome()).isEqualTo("NO");
    }

    @Test
    void shouldSkipBrokenConfiguredMarketAndContinue() {
        properties.setMarketSelectionMode(PolymarketProperties.SelectionMode.CONFIG);
        properties.setTrackedMarkets(List.of("broken", "good"));

        when(gammaApiClient.getMarketBySlug("broken"))
                .thenReturn(market("broken", "Broken", "not-json"));
        when(gammaApiClient.getMarketBySlug("good"))
                .thenReturn(market("good", "Good", "[\"good-yes\",\"good-no\"]"));

        List<TrackedMarket> result = service.resolveTrackedMarkets();

        assertThat(result).hasSize(2);
        assertThat(result)
                .extracting(TrackedMarket::assetId)
                .containsExactly("good-yes", "good-no");
    }

    @Test
    void shouldResolveAutoTopActiveMarkets() {
        properties.setMarketSelectionMode(PolymarketProperties.SelectionMode.AUTO_TOP_ACTIVE);
        properties.setAutoTrackCount(2);

        when(gammaApiClient.getActiveMarkets(2)).thenReturn(List.of(
                market("market-1", "Question 1", "[\"yes-1\",\"no-1\"]"),
                market("market-2", "Question 2", "[\"yes-2\",\"no-2\"]")
        ));

        List<TrackedMarket> result = service.resolveTrackedMarkets();

        assertThat(result).hasSize(2);
        assertThat(result)
                .extracting(TrackedMarket::assetId)
                .containsExactly("yes-1", "yes-2");
    }

    private GammaApiClient.GammaMarketDto market(String slug, String question, String tokenIds) {
        GammaApiClient.GammaMarketDto dto = new GammaApiClient.GammaMarketDto();
        dto.setSlug(slug);
        dto.setQuestion(question);
        dto.setClobTokenIds(tokenIds);
        dto.setActive(true);
        return dto;
    }
}