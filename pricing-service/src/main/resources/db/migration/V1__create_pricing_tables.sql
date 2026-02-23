ALTER TABLE orders ADD COLUMN IF NOT EXISTS pricing_status TEXT;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS final_price_cents INT;

CREATE TABLE pricing_results (
    order_id UUID PRIMARY KEY,
    event_id UUID NOT NULL,
    final_price_cents INT NOT NULL,
    tax_cents INT NOT NULL,
    discount_cents INT NOT NULL,
    computed_at TIMESTAMP NOT NULL
);

CREATE TABLE processed_events (
    event_id UUID PRIMARY KEY,
    order_id UUID NOT NULL,
    processed_at TIMESTAMP NOT NULL
);
