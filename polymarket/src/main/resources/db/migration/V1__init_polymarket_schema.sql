CREATE SCHEMA IF NOT EXISTS polymarket;

CREATE TABLE IF NOT EXISTS polymarket.price_change_event
(
    id BIGSERIAL PRIMARY KEY,
    asset_id VARCHAR(128) NOT NULL,
    market_slug VARCHAR(255),
    market_question TEXT,
    outcome VARCHAR(32),
    source_event_type VARCHAR(64) NOT NULL,
    old_price NUMERIC(20,8),
    new_price NUMERIC(20,8) NOT NULL,
    best_bid NUMERIC(20,8),
    best_ask NUMERIC(20,8),
    event_timestamp TIMESTAMPTZ,
    received_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_price_change_event_asset_received
    ON polymarket.price_change_event (asset_id, received_at DESC);

CREATE INDEX IF NOT EXISTS idx_price_change_event_market_slug
    ON polymarket.price_change_event (market_slug);

CREATE INDEX IF NOT EXISTS idx_price_change_event_received_at
    ON polymarket.price_change_event (received_at DESC);