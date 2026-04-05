package com.example.polymarket.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "price_change_event", schema = "polymarket")
public class PriceChangeEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "asset_id", nullable = false, length = 128)
    private String assetId;

    @Column(name = "market_slug")
    private String marketSlug;

    @Column(name = "market_question")
    private String marketQuestion;

    @Column(name = "outcome", length = 32)
    private String outcome;

    @Column(name = "source_event_type", nullable = false, length = 64)
    private String sourceEventType;

    @Column(name = "old_price", precision = 20, scale = 8)
    private BigDecimal oldPrice;

    @Column(name = "new_price", nullable = false, precision = 20, scale = 8)
    private BigDecimal newPrice;

    @Column(name = "best_bid", precision = 20, scale = 8)
    private BigDecimal bestBid;

    @Column(name = "best_ask", precision = 20, scale = 8)
    private BigDecimal bestAsk;

    @Column(name = "event_timestamp")
    private Instant eventTimestamp;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @PrePersist
    public void prePersist() {
        if (receivedAt == null) {
            receivedAt = Instant.now();
        }
    }
}