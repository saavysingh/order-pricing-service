import { OrderRequest, OrderResponse, TraceState, RecentOrder } from "./types";

const BASE_URL = import.meta.env.VITE_API_BASE_URL || "http://localhost:8080";

async function request<T>(path: string, options?: RequestInit): Promise<T> {
  const res = await fetch(`${BASE_URL}${path}`, {
    ...options,
    headers: {
      "Content-Type": "application/json",
      ...options?.headers,
    },
  });
  if (!res.ok) {
    const text = await res.text().catch(() => "Unknown error");
    throw new Error(`${res.status}: ${text}`);
  }
  return res.json();
}

type DemoTraceResponse = {
  orderId: string;
  eventId?: string | null;
  orderCreated: boolean;
  orderStatus?: string | null;
  pricingStatus?: "PENDING" | "PRICED" | "FAILED" | null;
  subtotalCents?: number | null;
  finalPriceCents?: number | null;
  outbox?: {
    status?: "NEW" | "PUBLISHED" | "FAILED" | null;
    attempts?: number | null;
    createdAt?: string | null;
    publishedAt?: string | null;
    nextAttemptAt?: string | null;
    lastError?: string | null;
  } | null;
  pricing?: {
    hasPricingResult?: boolean | null;
    computedAt?: string | null;
    taxCents?: number | null;
    discountCents?: number | null;
    processedEvent?: boolean | null;
    processedAt?: string | null;
  } | null;
  dlq?: {
    state?: "NONE" | "DLQ" | null;
    attempts?: number | null;
    lastError?: string | null;
    lastUpdatedAt?: string | null;
  } | null;
};

type DemoRecentOrder = {
  orderId: string;
  createdAt: string;
  pricingStatus: string;
  subtotalCents?: number | null;
  finalPriceCents?: number | null;
};

function mapTraceResponse(r: DemoTraceResponse): TraceState {
  return {
    orderCreated: r.orderCreated,
    outboxStatus: r.outbox?.status ?? undefined,
    publishedAt: r.outbox?.publishedAt ?? null,
    pricingStatus: r.pricingStatus ?? undefined,
    hasPricingResult: r.pricing?.hasPricingResult ?? false,
    processedEvent: r.pricing?.processedEvent ?? false,
    retryState: (r.dlq?.state ?? "NONE") as TraceState["retryState"],
    attempts: r.dlq?.attempts ?? r.outbox?.attempts ?? undefined,
    lastError: r.dlq?.lastError ?? r.outbox?.lastError ?? null,
  };
}

export function placeOrder(order: OrderRequest, idempotencyKey: string): Promise<OrderResponse> {
  return request<OrderResponse>("/v1/orders", {
    method: "POST",
    body: JSON.stringify(order),
    headers: { "Idempotency-Key": idempotencyKey },
  });
}

export function fetchTrace(orderId?: string, eventId?: string, signal?: AbortSignal): Promise<TraceState> {
  const param = eventId ? `eventId=${eventId}` : `orderId=${orderId}`;
  return request<DemoTraceResponse>(`/demo/trace?${param}`, { signal }).then(mapTraceResponse);
}

export function fetchRecentOrders(): Promise<RecentOrder[]> {
  return request<DemoRecentOrder[]>("/demo/recent").then((rows) =>
    rows.map((r) => ({
      order_id: r.orderId,
      created_at: r.createdAt,
      pricing_status: r.pricingStatus,
    }))
  );
}
