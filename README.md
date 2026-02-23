# CheckoutService (Backend)

Distributed order processing demo built around an outbox pattern with Kafka, Postgres, and Spring Boot. The backend is split into two services: `order-service` (API + outbox publisher) and `pricing-service` (Kafka consumer + pricing/DLQ handling).

## Architecture overview

1. Client calls `POST /v1/orders` on `order-service`.
2. `order-service` writes an `orders` row and an `outbox_events` row.
3. Outbox publisher emits `order.created` to Kafka.
4. `pricing-service` consumes `order.created`, calculates price, and writes `pricing_results` + `processed_events`.
5. Failures are retried; after max attempts, the event goes to DLQ and `dlq_events` is recorded.

## Services

### order-service

- Spring Boot API on port 8080
- Outbox pattern with `outbox_events`
- Idempotency via `Idempotency-Key`
- Demo/trace endpoints for UI visualization

### pricing-service

- Kafka consumer for `order.created` (and retry topic)
- Computes pricing, persists results
- Records `processed_events`
- On failure: retry with backoff; after max attempts, sends to DLQ and writes `dlq_events`

## Data model (Postgres)

Key tables:

- `orders`: canonical order rows
- `outbox_events`: order-created events to publish
- `pricing_results`: computed pricing output
- `processed_events`: idempotent consumer tracking
- `dlq_events`: DLQ tracking metadata
- `idempotency_keys`: request dedupe ledger

## Kafka topics

- `order.created` — primary event
- `order.created.retry` — retry pipeline
- `order.created.dlq` — dead letter events

## API endpoints (order-service)

### Create order

`POST /v1/orders`

Requires `Idempotency-Key` header. Same key + same payload returns the original response; reuse with different payload returns 409.

### Get order

`GET /v1/orders/{orderId}`

Returns order details and the latest `event_id` (if available).

### Demo/trace endpoints (read-only)

These endpoints are safe to poll from a local UI dashboard.

- `GET /demo/trace?orderId=<uuid>` or `GET /demo/trace?eventId=<uuid>`
	- Aggregated view of an order’s journey: outbox status, Kafka publish, pricing result, DLQ state.
- `GET /demo/recent?limit=25`
	- Recent orders with pricing status.
- `GET /demo/orders?limit=20`
	- Legacy list with basic order fields.

## Failure simulation

Pricing failures are simulated via promo codes (in `pricing-service`):

- `promo_code = FAIL` → always fails, ends in DLQ
- `promo_code = RETRY2` → fails a few attempts, then succeeds

When DLQ occurs, `pricing_status` is set to `FAILED` on the order row and `dlq_events` is updated with attempt/error info.

## Local development

### Prerequisites

- Java 17+ (services run on Java 17; tests may run with newer JDKs)
- Maven
- Docker (for Postgres + Kafka)

### Infra (Postgres + Kafka)

Infra is defined under [order-service/infra/docker-compose.yml](order-service/infra/docker-compose.yml). Bring it up before running services.

### Run services

- order-service: run from [order-service](order-service)
- pricing-service: run from [pricing-service](pricing-service)

## CORS (local UI)

CORS is enabled for `/demo/**` and `/v1/**`. Allowed origins (default) include:

- http://localhost:5173
- http://localhost:3000
- http://localhost:8081
- https://*.lovable.app

Override with `demo.cors.allowed-origins` in `order-service` config.

## Notes

- Idempotency records are stored in `idempotency_keys`.
- Outbox publishes `ORDER_CREATED` events.
- `demo/trace` uses `pricing_results`, `processed_events`, and `dlq_events` to show end-to-end state.