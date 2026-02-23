import { TimelineEntry } from "@/lib/types";
import { cn } from "@/lib/utils";
import { ScrollArea } from "@/components/ui/scroll-area";

interface Props {
  entries: TimelineEntry[];
}

const dotColor: Record<string, string> = {
  idle: "bg-status-idle",
  progress: "bg-status-progress",
  success: "bg-status-success",
  error: "bg-status-error",
};

export default function EventLog({ entries }: Props) {
  return (
    <div className="space-y-3">
      <h2 className="text-sm font-semibold uppercase tracking-wider text-muted-foreground">
        Event Log
      </h2>
      <ScrollArea className="h-[500px] pr-2">
        {entries.length === 0 ? (
          <p className="text-xs text-muted-foreground italic">No events yet. Place an order to begin.</p>
        ) : (
          <div className="space-y-1">
            {entries.map((e, i) => (
              <div key={i} className="flex items-start gap-2 py-1">
                <div className={cn("w-2 h-2 rounded-full mt-1 flex-shrink-0", dotColor[e.status])} />
                <div className="min-w-0">
                  <p className="text-xs text-foreground">{e.message}</p>
                  <p className="text-[10px] text-muted-foreground">
                    {e.timestamp.toLocaleTimeString()}
                  </p>
                </div>
              </div>
            ))}
          </div>
        )}
      </ScrollArea>
    </div>
  );
}
