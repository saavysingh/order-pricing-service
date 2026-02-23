ALTER TABLE orders ADD COLUMN items_json JSONB;

CREATE TABLE idempotency_keys (
    idempotency_key TEXT PRIMARY KEY,
    request_hash TEXT NOT NULL,
    order_id UUID,
    status_code INT NOT NULL,
    response_body TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
