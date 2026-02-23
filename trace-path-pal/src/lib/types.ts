export interface OrderItem {
  sku: string;
  qty: number;
  unit_price_cents: number;
}

export interface OrderRequest {
  customer_id: string;
  currency: string;
  items: OrderItem[];
  promo_code?: string;
}

export interface OrderResponse {
  order_id: string;
  event_id?: string;
}

export interface TraceState {
  orderCreated?: boolean;
  outboxStatus?: "NEW" | "PUBLISHED" | "FAILED";
  publishedAt?: string | null;
  pricingStatus?: "PENDING" | "PRICED" | "FAILED";
  hasPricingResult?: boolean;
  processedEvent?: boolean;
  retryState?: "NONE" | "RETRYING" | "DLQ";
  attempts?: number;
  lastError?: string | null;
}

export interface RecentOrder {
  order_id: string;
  created_at: string;
  pricing_status: string;
}

export type NodeStatus = "idle" | "progress" | "success" | "error";

export interface TimelineEntry {
  timestamp: Date;
  message: string;
  status: NodeStatus;
}
