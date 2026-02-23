CREATE TABLE orders (
    order_id UUID PRIMARY KEY,
    customer_id TEXT,
    currency TEXT,
    subtotal_cents INT,
    status TEXT DEFAULT 'CREATED',
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);
