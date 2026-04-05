package com.example.polymarket.service;

import com.example.polymarket.dto.PriceSnapshot;
import com.example.polymarket.entity.PriceChangeEventEntity;
import com.example.polymarket.repository.PriceChangeEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

@Service
public class PriceUpdateService {

    private final PriceStateStore stateStore;
    private final PriceChangeEventRepository repository;

    public PriceUpdateService(
            PriceStateStore stateStore,
            PriceChangeEventRepository repository
    ) {
        this.stateStore = stateStore;
        this.repository = repository;
    }

    @Transactional
    public void handlePriceUpdate(PriceSnapshot snapshot) {
        Optional<BigDecimal> previous = stateStore.getLastPrice(snapshot.assetId());

        if (previous.isPresent() && previous.get().compareTo(snapshot.price()) == 0) {
            return;
        }

        PriceChangeEventEntity entity = new PriceChangeEventEntity();
        entity.setAssetId(snapshot.assetId());
        entity.setMarketSlug(snapshot.marketSlug());
        entity.setMarketQuestion(snapshot.question());
        entity.setOutcome(snapshot.outcome());
        entity.setSourceEventType(snapshot.sourceEventType());
        entity.setOldPrice(previous.orElse(null));
        entity.setNewPrice(snapshot.price());
        entity.setBestBid(snapshot.bestBid());
        entity.setBestAsk(snapshot.bestAsk());
        entity.setEventTimestamp(snapshot.eventTimestamp());

        repository.save(entity);
        stateStore.saveLastPrice(snapshot.assetId(), snapshot.price());
    }
}