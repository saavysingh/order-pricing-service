import { TraceState, NodeStatus } from "@/lib/types";
import StatusNode from "./StatusNode";
import {
  Globe, Database, Inbox, Send, Radio, Calculator, FileCheck, AlertTriangle,
  LucideIcon,
} from "lucide-react";
import { cn } from "@/lib/utils";

interface Props {
  trace: TraceState | null;
}

interface NodeDef {
  label: string;
  sublabel?: string;
  status: NodeStatus;
  icon: LucideIcon;
  pastel: string;
}

const PASTEL_KEYS = [
  "node-api", "node-postgres", "node-outbox", "node-publisher",
  "node-kafka", "node-pricing", "node-results", "node-retry",
];

function deriveStatuses(t: TraceState | null): NodeDef[] {
  if (!t) {
    return [
      { label: "Order API", icon: Globe, status: "idle", pastel: PASTEL_KEYS[0] },
      { label: "Postgres", sublabel: "orders", icon: Database, status: "idle", pastel: PASTEL_KEYS[1] },
      { label: "Outbox", sublabel: "outbox_events", icon: Inbox, status: "idle", pastel: PASTEL_KEYS[2] },
      { label: "Publisher", icon: Send, status: "idle", pastel: PASTEL_KEYS[3] },
      { label: "Kafka", sublabel: "order.created", icon: Radio, status: "idle", pastel: PASTEL_KEYS[4] },
      { label: "Pricing", sublabel: "Consumer", icon: Calculator, status: "idle", pastel: PASTEL_KEYS[5] },
      { label: "Results", sublabel: "Persisted", icon: FileCheck, status: "idle", pastel: PASTEL_KEYS[6] },
      { label: "Retry / DLQ", icon: AlertTriangle, status: "idle", pastel: PASTEL_KEYS[7] },
    ];
  }

  const orderStatus: NodeStatus = t.orderCreated ? "success" : "progress";
  const outboxStatus: NodeStatus =
    t.outboxStatus === "PUBLISHED" ? "success" :
    t.outboxStatus === "FAILED" ? "error" :
    t.outboxStatus === "NEW" ? "progress" : "idle";
  const publisherStatus: NodeStatus =
    t.publishedAt ? "success" :
    t.outboxStatus === "PUBLISHED" ? "success" :
    outboxStatus === "success" ? "progress" : "idle";
  const kafkaStatus: NodeStatus =
    t.processedEvent ? "success" :
    publisherStatus === "success" ? "progress" : "idle";
  const pricingConsumerStatus: NodeStatus =
    t.pricingStatus === "PRICED" ? "success" :
    t.pricingStatus === "FAILED" ? "error" :
    t.pricingStatus === "PENDING" ? "progress" : "idle";
  const pricingResultStatus: NodeStatus =
    t.hasPricingResult ? "success" :
    pricingConsumerStatus === "success" ? "progress" : "idle";
  const retryStatus: NodeStatus =
    t.retryState === "DLQ" ? "error" :
    t.retryState === "RETRYING" ? "progress" : "idle";

  return [
    { label: "Order API", icon: Globe, status: orderStatus, pastel: PASTEL_KEYS[0] },
    { label: "Postgres", sublabel: "orders", icon: Database, status: orderStatus, pastel: PASTEL_KEYS[1] },
    { label: "Outbox", sublabel: t.outboxStatus || "—", icon: Inbox, status: outboxStatus, pastel: PASTEL_KEYS[2] },
    { label: "Publisher", sublabel: t.publishedAt ? "sent" : undefined, icon: Send, status: publisherStatus, pastel: PASTEL_KEYS[3] },
    { label: "Kafka", sublabel: "order.created", icon: Radio, status: kafkaStatus, pastel: PASTEL_KEYS[4] },
    { label: "Pricing", sublabel: t.pricingStatus || "—", icon: Calculator, status: pricingConsumerStatus, pastel: PASTEL_KEYS[5] },
    { label: "Results", sublabel: t.hasPricingResult ? "persisted" : "—", icon: FileCheck, status: pricingResultStatus, pastel: PASTEL_KEYS[6] },
    { label: "Retry / DLQ", sublabel: t.retryState !== "NONE" ? `${t.retryState} (${t.attempts ?? 0})` : undefined, icon: AlertTriangle, status: retryStatus, pastel: PASTEL_KEYS[7] },
  ];
}

function connectorColor(left: NodeStatus, right: NodeStatus): string {
  if (left === "success" && right === "success") return "bg-status-success/50";
  if (left === "success" && right === "progress") return "bg-status-progress/50 animate-pulse-glow";
  if (left === "error" || right === "error") return "bg-status-error/40";
  return "bg-border";
}

export default function ArchitectureDiagram({ trace }: Props) {
  const nodes = deriveStatuses(trace);

  return (
    <div className="space-y-4">
      <div className="flex items-center gap-3 flex-wrap">
        <h2 className="text-sm font-semibold uppercase tracking-wider text-muted-foreground">
          Architecture Trace
        </h2>
        <div className="flex items-center gap-3 ml-auto">
          {(["idle", "progress", "success", "error"] as const).map((s) => (
            <div key={s} className="flex items-center gap-1.5">
              <div className={cn(
                "w-2 h-2 rounded-full",
                s === "idle" ? "bg-status-idle" :
                s === "progress" ? "bg-status-progress" :
                s === "success" ? "bg-status-success" :
                "bg-status-error"
              )} />
              <span className="text-[10px] text-muted-foreground capitalize">{s}</span>
            </div>
          ))}
        </div>
      </div>

      {/* Pipeline — wrapping grid, no horizontal scroll */}
      <div className="rounded-xl border border-border bg-muted/20 p-6">
        <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 gap-4">
          {nodes.map((node, i) => (
            <div key={node.label + i} className="relative">
              <StatusNode
                label={node.label}
                sublabel={node.sublabel}
                status={node.status}
                icon={node.icon}
                index={i}
                pastelClass={node.pastel}
              />
              {/* Step number badge */}
              <div
                className="absolute -top-2 -left-2 w-5 h-5 rounded-full bg-secondary text-[10px] font-bold flex items-center justify-center text-muted-foreground border border-border"
              >
                {i + 1}
              </div>
            </div>
          ))}
        </div>

        {/* Flow arrows between rows — visual connector bar */}
        <div className="mt-4 flex items-center justify-center gap-1">
          {nodes.map((node, i) => (
            <div key={`dot-${i}`} className="flex items-center gap-1">
              <div
                className={cn(
                  "w-2.5 h-2.5 rounded-full transition-all duration-500",
                  node.status === "idle" ? "bg-muted-foreground/20" :
                  node.status === "progress" ? "bg-status-progress animate-pulse-glow" :
                  node.status === "success" ? "bg-status-success" :
                  "bg-status-error"
                )}
                style={{
                  backgroundColor: node.status !== "idle" && node.status !== "error"
                    ? `hsl(var(--${node.pastel}))`
                    : undefined,
                }}
              />
              {i < nodes.length - 1 && (
                <div className={cn(
                  "w-4 h-[2px] rounded-full transition-all duration-500",
                  connectorColor(nodes[i].status, nodes[i + 1].status)
                )} />
              )}
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
