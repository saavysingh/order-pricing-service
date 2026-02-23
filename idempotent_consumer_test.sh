#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="/Users/saavysingh/Desktop/CheckoutService"
INFRA_DIR="$ROOT_DIR/order-service/infra"
API_BASE="http://localhost:8080"

echo "Step 1: Create 1 real order"
create_order_response=$(curl -s -X POST "$API_BASE/v1/orders" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: replay-test-1" \
  -d '{"customer_id":"cust-001","currency":"USD","items":[{"sku":"WIDGET-A","qty":2,"unit_price_cents":1500}]}'
)

if [[ -z "$create_order_response" ]]; then
  echo "Empty response from order-service" >&2
  exit 1
fi

export RESP="$create_order_response"
order_id=$(python3 -c 'import json,os; print(json.loads(os.environ["RESP"])["order_id"])')
event_id=$(python3 -c 'import json,os; print(json.loads(os.environ["RESP"]).get("event_id", ""))')

echo "order_id=$order_id"
if [[ -z "$event_id" ]]; then
  echo "event_id missing in response; will derive from outbox"
fi

echo "Step 2: Replay the exact Kafka event many times"
cd "$INFRA_DIR"

payload=$(docker compose exec -T postgres psql -U postgres -d order_service -t -A \
  -c "select payload from outbox_events where aggregate_id = '$order_id' and event_type = 'ORDER_CREATED' order by created_at desc limit 1;" \
  | tr -d '\r'
)

if [[ -z "$payload" ]]; then
  echo "Failed to fetch outbox payload for order_id=$order_id" >&2
  exit 1
fi

if [[ -z "$event_id" ]]; then
  event_id=$(python3 -c 'import json,sys; print(json.loads(sys.argv[1])["event_id"])' "$payload")
fi

python3 - <<PY | docker compose exec -T kafka kafka-console-producer --broker-list kafka:9092 --topic order.created >/dev/null
payload = '''$payload'''
for _ in range(1000):
    print(payload)
PY

echo "Replayed 1000 messages for order_id=$order_id event_id=$event_id"

echo "Step 3: Check DB invariant"
docker compose exec -T postgres psql -U postgres -d order_service -c "select count(*) from processed_events where event_id = '$event_id';"
docker compose exec -T postgres psql -U postgres -d order_service -c "select count(*) from pricing_results where order_id = '$order_id';"
