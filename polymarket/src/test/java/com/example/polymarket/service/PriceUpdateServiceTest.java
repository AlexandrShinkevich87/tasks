package com.example.polymarket.service;

import com.example.polymarket.dto.PriceSnapshot;
import com.example.polymarket.repository.PriceChangeEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PriceUpdateServiceTest {

    @Mock
    private PriceStateStore stateStore;

    @Mock
    private PriceChangeEventRepository repository;

    private PriceUpdateService service;

    @BeforeEach
    void setUp() {
        service = new PriceUpdateService(stateStore, repository);
    }

    @Test
    void shouldSaveFirstPriceWhenNoPreviousValue() {
        PriceSnapshot snapshot = snapshot("0.55");

        when(stateStore.getLastPrice("asset-1")).thenReturn(Optional.empty());

        service.handlePriceUpdate(snapshot);

        verify(repository).save(any());
        verify(stateStore).saveLastPrice("asset-1", new BigDecimal("0.55"));
    }

    @Test
    void shouldSkipWhenPriceDidNotChange() {
        PriceSnapshot snapshot = snapshot("0.55");

        when(stateStore.getLastPrice("asset-1"))
                .thenReturn(Optional.of(new BigDecimal("0.55")));

        service.handlePriceUpdate(snapshot);

        verify(repository, never()).save(any());
        verify(stateStore, never()).saveLastPrice(any(), any());
    }

    @Test
    void shouldSaveChangedPriceWithPreviousValue() {
        PriceSnapshot snapshot = snapshot("0.57");

        when(stateStore.getLastPrice("asset-1"))
                .thenReturn(Optional.of(new BigDecimal("0.55")));

        service.handlePriceUpdate(snapshot);

        ArgumentCaptor<com.example.polymarket.entity.PriceChangeEventEntity> captor =
                ArgumentCaptor.forClass(com.example.polymarket.entity.PriceChangeEventEntity.class);

        verify(repository).save(captor.capture());
        verify(stateStore).saveLastPrice("asset-1", new BigDecimal("0.57"));

        assertThat(captor.getValue().getOldPrice()).isEqualByComparingTo("0.55");
        assertThat(captor.getValue().getNewPrice()).isEqualByComparingTo("0.57");
        assertThat(captor.getValue().getAssetId()).isEqualTo("asset-1");
    }

    private PriceSnapshot snapshot(String price) {
        return new PriceSnapshot(
                "asset-1",
                "market-slug",
                "Question?",
                "YES",
                "best_bid_ask",
                new BigDecimal(price),
                new BigDecimal("0.54"),
                new BigDecimal("0.56"),
                Instant.parse("2026-04-05T10:00:00Z")
        );
    }
}