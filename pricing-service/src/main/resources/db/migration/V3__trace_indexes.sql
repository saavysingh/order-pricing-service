CREATE INDEX IF NOT EXISTS idx_pricing_results_order_id ON pricing_results (order_id);
CREATE INDEX IF NOT EXISTS idx_processed_events_event_id ON processed_events (event_id);
CREATE INDEX IF NOT EXISTS idx_dlq_events_order_id ON dlq_events (order_id);