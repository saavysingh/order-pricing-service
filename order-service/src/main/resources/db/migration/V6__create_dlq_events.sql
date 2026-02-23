CREATE TABLE IF NOT EXISTS dlq_events (
    event_id UUID PRIMARY KEY,
    order_id UUID NOT NULL,
    topic TEXT NOT NULL,
    attempts INT NOT NULL,
    error TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_dlq_events_order_id ON dlq_events (order_id);