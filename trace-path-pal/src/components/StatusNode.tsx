import { NodeStatus } from "@/lib/types";
import { cn } from "@/lib/utils";
import { LucideIcon } from "lucide-react";

interface Props {
  label: string;
  sublabel?: string;
  status: NodeStatus;
  icon: LucideIcon;
  index: number;
  pastelClass: string; // e.g. "node-api"
}

const statusDot: Record<NodeStatus, string> = {
  idle: "bg-status-idle",
  progress: "bg-status-progress animate-pulse-glow",
  success: "bg-status-success",
  error: "bg-status-error animate-pulse-glow",
};

const statusGlow: Record<NodeStatus, string> = {
  idle: "",
  progress: "glow-progress",
  success: "glow-success",
  error: "glow-error",
};

export default function StatusNode({ label, sublabel, status, icon: Icon, index, pastelClass }: Props) {
  return (
    <div
      className={cn(
        "relative rounded-xl border bg-card p-4 min-w-[130px] text-center transition-all duration-500 ease-out",
        "hover:scale-105 hover:z-10",
        "animate-fade-in",
        status === "idle" ? "border-border" : status === "error" ? "border-status-error/50" : `border-${pastelClass}/40`,
        statusGlow[status]
      )}
      style={{ animationDelay: `${index * 80}ms`, animationFillMode: "backwards" }}
    >
      {/* Status dot */}
      <div className={cn(
        "absolute top-2.5 right-2.5 w-2 h-2 rounded-full transition-colors duration-300",
        statusDot[status]
      )} />

      {/* Icon with pastel background */}
      <div
        className="mx-auto mb-2 w-10 h-10 rounded-xl flex items-center justify-center transition-all duration-300"
        style={{
          backgroundColor: `hsl(var(--${pastelClass.replace("node-", "node-")}) / ${status === "idle" ? 0.12 : 0.25})`,
        }}
      >
        <Icon
          className="w-5 h-5 transition-colors duration-300"
          style={{
            color: status === "error"
              ? "hsl(var(--status-error))"
              : `hsl(var(--${pastelClass.replace("node-", "node-")}))`,
          }}
        />
      </div>

      {/* Label */}
      <p className={cn(
        "text-xs font-bold tracking-wide",
        status === "idle" ? "text-muted-foreground" :
        status === "error" ? "text-status-error" :
        "text-foreground"
      )}>{label}</p>
      {sublabel && (
        <p className="text-[10px] text-muted-foreground mt-0.5 font-medium">{sublabel}</p>
      )}

      {/* Bottom accent bar */}
      <div
        className="absolute bottom-0 left-3 right-3 h-[2px] rounded-full transition-all duration-500"
        style={{
          backgroundColor: status === "idle"
            ? "hsl(var(--border))"
            : status === "error"
            ? "hsl(var(--status-error) / 0.6)"
            : `hsl(var(--${pastelClass.replace("node-", "node-")}) / 0.6)`,
        }}
      />
    </div>
  );
}
