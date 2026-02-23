import { useState, useEffect, useRef, useCallback } from "react";
import { TraceState, TimelineEntry, NodeStatus } from "@/lib/types";
import { fetchTrace } from "@/lib/api";
import OrderForm from "@/components/OrderForm";
import ArchitectureDiagram from "@/components/ArchitectureDiagram";
import EventLog from "@/components/EventLog";
import RecentOrders from "@/components/RecentOrders";
import { AlertCircle, Zap } from "lucide-react";

export default function Index() {
  const [trace, setTrace] = useState<TraceState | null>(null);
  const [timeline, setTimeline] = useState<TimelineEntry[]>([]);
  const [activeOrderId, setActiveOrderId] = useState<string | null>(null);
  const [activeEventId, setActiveEventId] = useState<string | undefined>();
  const [networkError, setNetworkError] = useState<string | null>(null);
  const pollingRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const pollingTokenRef = useRef(0);
  const abortRef = useRef<AbortController | null>(null);
  const prevTraceRef = useRef<TraceState | null>(null);

  const addTimelineEntry = useCallback((message: string, status: NodeStatus) => {
    setTimeline((prev) => [{ timestamp: new Date(), message, status }, ...prev]);
  }, []);

  const diffTrace = useCallback(
    (prev: TraceState | null, next: TraceState) => {
      if (!prev?.orderCreated && next.orderCreated) addTimelineEntry("Order created in Postgres", "success");
      if (prev?.outboxStatus !== "NEW" && next.outboxStatus === "NEW") addTimelineEntry("Outbox row created (NEW)", "progress");
      if (prev?.outboxStatus !== "PUBLISHED" && next.outboxStatus === "PUBLISHED") addTimelineEntry("Outbox published", "success");
      if (prev?.outboxStatus !== "FAILED" && next.outboxStatus === "FAILED") addTimelineEntry("Outbox FAILED", "error");
      if (!prev?.publishedAt && next.publishedAt) addTimelineEntry("Published to Kafka", "success");
      if (prev?.pricingStatus !== "PENDING" && next.pricingStatus === "PENDING") addTimelineEntry("Pricing consumer processing…", "progress");
      if (prev?.pricingStatus !== "PRICED" && next.pricingStatus === "PRICED") addTimelineEntry("Pricing completed", "success");
      if (prev?.pricingStatus !== "FAILED" && next.pricingStatus === "FAILED") addTimelineEntry("Pricing FAILED", "error");
      if (!prev?.hasPricingResult && next.hasPricingResult) addTimelineEntry("Pricing result persisted", "success");
      if (prev?.retryState !== "RETRYING" && next.retryState === "RETRYING")
        addTimelineEntry(`Sent to retry (attempt ${next.attempts ?? "?"})`, "progress");
      if (prev?.retryState !== "DLQ" && next.retryState === "DLQ") addTimelineEntry("Sent to DLQ", "error");
      if (next.lastError && next.lastError !== prev?.lastError)
        addTimelineEntry(`Error: ${next.lastError}`, "error");
    },
    [addTimelineEntry]
  );

  const startPolling = useCallback(
    (orderId?: string, eventId?: string) => {
      if (pollingRef.current) clearTimeout(pollingRef.current);
      abortRef.current?.abort();
      prevTraceRef.current = null;
      const token = ++pollingTokenRef.current;

      const pollOnce = async () => {
        if (pollingTokenRef.current !== token) return;
        const controller = new AbortController();
        abortRef.current = controller;
        try {
          const t = await fetchTrace(orderId, eventId, controller.signal);
          if (pollingTokenRef.current !== token) return;
          setNetworkError(null);
          diffTrace(prevTraceRef.current, t);
          prevTraceRef.current = t;
          setTrace(t);

          // Stop polling when fully complete
          if (t.retryState === "DLQ") {
            return;
          }
          if (t.hasPricingResult && (t.retryState === "NONE" || !t.retryState)) {
            return;
          }
        } catch (e: any) {
          if (pollingTokenRef.current !== token || e.name === "AbortError") return;
          setNetworkError(e.message);
        }

        pollingRef.current = setTimeout(pollOnce, 750);
      };

      void pollOnce();
    },
    [diffTrace]
  );

  const handleOrderPlaced = useCallback(
    (orderId: string, eventId?: string) => {
      setActiveOrderId(orderId);
      setActiveEventId(eventId);
      setTrace(null);
      setTimeline([{ timestamp: new Date(), message: `Order submitted (${orderId})`, status: "progress" }]);
      startPolling(orderId, eventId);
    },
    [startPolling]
  );

  const handleSelectOrder = useCallback(
    (orderId: string) => {
      setActiveOrderId(orderId);
      setActiveEventId(undefined);
      setTrace(null);
      setTimeline([{ timestamp: new Date(), message: `Loading trace for ${orderId}`, status: "progress" }]);
      startPolling(orderId);
    },
    [startPolling]
  );

  useEffect(() => {
    return () => {
      if (pollingRef.current) clearTimeout(pollingRef.current);
      abortRef.current?.abort();
    };
  }, []);

  return (
    <div className="min-h-screen bg-background">
      {/* Network error banner */}
      {networkError && (
        <div className="bg-destructive/10 border-b border-destructive/30 px-4 py-2 flex items-center gap-2">
          <AlertCircle className="w-4 h-4 text-destructive" />
          <span className="text-xs text-destructive">{networkError}</span>
        </div>
      )}

      {/* Header */}
      <header className="border-b border-border px-6 py-4">
        <div className="flex items-center gap-3">
          <div className="w-8 h-8 rounded-md bg-primary/20 flex items-center justify-center">
            <Zap className="w-4 h-4 text-primary" />
          </div>
          <div>
            <h1 className="text-base font-bold text-foreground">Distributed Systems Demo</h1>
            <p className="text-xs text-muted-foreground">
              Place an order → watch it flow through API → Postgres → Outbox → Kafka → Pricing
            </p>
          </div>
        </div>
      </header>

      {/* Main layout */}
      <div className="grid grid-cols-1 lg:grid-cols-[280px_1fr_260px] gap-0 min-h-[calc(100vh-73px)]">
        {/* Left panel - Order Form */}
        <aside className="border-r border-border p-4 overflow-auto">
          <OrderForm onOrderPlaced={handleOrderPlaced} />
        </aside>

        {/* Center panel - Architecture */}
        <main className="p-6 overflow-auto space-y-6">
          <ArchitectureDiagram trace={trace} />

          {/* Recent orders */}
          <RecentOrders onSelect={handleSelectOrder} />
        </main>

        {/* Right panel - Event Log */}
        <aside className="border-l border-border p-4 overflow-auto">
          <EventLog entries={timeline} />
        </aside>
      </div>
    </div>
  );
}
