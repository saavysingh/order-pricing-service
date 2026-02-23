#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="/Users/saavysingh/Desktop/CheckoutService"
INFRA_DIR="$ROOT_DIR/order-service/infra"
API_BASE="http://localhost:8080"

cd "$INFRA_DIR"

echo "Step 1: Baseline counts"
BASELINE_BACKLOG=$(docker compose exec -T postgres psql -U postgres -d order_service -t -A -c "select count(*) from outbox_events where status <> 'PUBLISHED';" | tr -d '\r')
BASELINE_PRICING=$(docker compose exec -T postgres psql -U postgres -d order_service -t -A -c "select count(*) from pricing_results;" | tr -d '\r')

echo "baseline_backlog=$BASELINE_BACKLOG"
echo "baseline_pricing_results=$BASELINE_PRICING"

echo "Step 2: Stop Kafka"
docker compose stop kafka

N=500

echo "Step 3: Create $N orders while Kafka is down"
for i in $(seq 1 $N); do
  curl -s -X POST "$API_BASE/v1/orders" \
    -H "Content-Type: application/json" \
    -H "Idempotency-Key: outage-$i-$(uuidgen)" \
    -d '{"customer_id":"cust-001","currency":"USD","items":[{"sku":"WIDGET-A","qty":2,"unit_price_cents":1500}]}' >/dev/null
  if (( i % 50 == 0 )); then
    echo "created $i / $N"
  fi
 done

echo "Step 4: Backlog after outage burst"
BACKLOG_AFTER=$(docker compose exec -T postgres psql -U postgres -d order_service -t -A -c "select count(*) from outbox_events where status <> 'PUBLISHED';" | tr -d '\r')

echo "backlog_after_outage=$BACKLOG_AFTER"

echo "Step 5: Start Kafka"
T0=$(date +%s)
docker compose start kafka

echo "Waiting for drain..."
sleep 20

BACKLOG_AFTER_RECOVERY=$(docker compose exec -T postgres psql -U postgres -d order_service -t -A -c "select count(*) from outbox_events where status <> 'PUBLISHED';" | tr -d '\r')
PUBLISHED_AFTER=$(docker compose exec -T postgres psql -U postgres -d order_service -t -A -c "select count(*) from outbox_events where status = 'PUBLISHED';" | tr -d '\r')
PRICING_AFTER=$(docker compose exec -T postgres psql -U postgres -d order_service -t -A -c "select count(*) from pricing_results;" | tr -d '\r')

T1=$(date +%s)
DRAIN_SECONDS=$((T1 - T0))

cat <<SUMMARY

=== Kafka Outage Drill Summary ===
N=$N
baseline_backlog=$BASELINE_BACKLOG
backlog_after_outage=$BACKLOG_AFTER
backlog_after_recovery=$BACKLOG_AFTER_RECOVERY
published_total=$PUBLISHED_AFTER
pricing_results_total=$PRICING_AFTER
kafka_restart_time=$T0
backlog_check_time=$T1
drain_time_seconds=$DRAIN_SECONDS
=================================
SUMMARY
