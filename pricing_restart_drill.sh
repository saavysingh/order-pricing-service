#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="/Users/saavysingh/Desktop/CheckoutService"
INFRA_DIR="$ROOT_DIR/order-service/infra"
API_BASE="http://localhost:8080"

cd "$INFRA_DIR"

echo "Step 1: Baseline counts"
BASELINE_PRICING=$(docker compose exec -T postgres psql -U postgres -d order_service -t -A -c "select count(*) from pricing_results;" | tr -d '\r')
BASELINE_PENDING=$(docker compose exec -T postgres psql -U postgres -d order_service -t -A -c "select count(*) from orders where pricing_status <> 'PRICED';" | tr -d '\r')

echo "baseline_pricing_results=$BASELINE_PRICING"
echo "baseline_pending_orders=$BASELINE_PENDING"

echo "Step 2: Stop pricing-service (only)"
cd "$ROOT_DIR/pricing-service"
pkill -f "PricingServiceApplication" >/dev/null 2>&1 || true

N=500

echo "Step 3: Create $N orders while pricing is down"
for i in $(seq 1 $N); do
  curl -s -X POST "$API_BASE/v1/orders" \
    -H "Content-Type: application/json" \
    -H "Idempotency-Key: pricing-restart-$i-$(uuidgen)" \
    -d '{"customer_id":"cust-001","currency":"USD","items":[{"sku":"WIDGET-A","qty":2,"unit_price_cents":1500}]}' >/dev/null
  if (( i % 50 == 0 )); then
    echo "created $i / $N"
  fi
 done

cd "$INFRA_DIR"
PRICING_DURING=$(docker compose exec -T postgres psql -U postgres -d order_service -t -A -c "select count(*) from pricing_results;" | tr -d '\r')
echo "pricing_results_while_down=$PRICING_DURING"

echo "Step 4: Start pricing-service again"
cd "$ROOT_DIR/pricing-service"
nohup mvn -f pom.xml spring-boot:run >/tmp/pricing-service.log 2>&1 &
T0=$(date +%s)

cd "$INFRA_DIR"

echo "Step 5: Wait for catch-up"
TARGET=$((BASELINE_PRICING + N))
ATTEMPTS=60
SLEEP_SECONDS=2

for i in $(seq 1 $ATTEMPTS); do
  CURRENT=$(docker compose exec -T postgres psql -U postgres -d order_service -t -A -c "select count(*) from pricing_results;" | tr -d '\r')
  echo "pricing_results=$CURRENT (target=$TARGET)"
  if [[ "$CURRENT" -ge "$TARGET" ]]; then
    break
  fi
  sleep $SLEEP_SECONDS
 done

T1=$(date +%s)
CATCHUP_SECONDS=$((T1 - T0))

FINAL_PRICING=$(docker compose exec -T postgres psql -U postgres -d order_service -t -A -c "select count(*) from pricing_results;" | tr -d '\r')
DLQ_COUNT=$(docker compose exec -T postgres psql -U postgres -d order_service -t -A -c "select count(*) from dlq_events;" | tr -d '\r')

cat <<SUMMARY

=== Pricing Consumer Restart Drill Summary ===
N=$N
baseline_pricing_results=$BASELINE_PRICING
final_pricing_results=$FINAL_PRICING
catch_up_seconds=$CATCHUP_SECONDS
pricing_results_while_down=$PRICING_DURING
dlq_events_total=$DLQ_COUNT
=============================================
SUMMARY
