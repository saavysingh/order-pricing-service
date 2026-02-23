import { useEffect, useState } from "react";
import { RecentOrder } from "@/lib/types";
import { fetchRecentOrders } from "@/lib/api";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";

interface Props {
  onSelect: (orderId: string) => void;
}

export default function RecentOrders({ onSelect }: Props) {
  const [orders, setOrders] = useState<RecentOrder[]>([]);
  const [error, setError] = useState(false);

  useEffect(() => {
    fetchRecentOrders()
      .then(setOrders)
      .catch(() => setError(true));
  }, []);

  if (error) return null; // silently hide if endpoint unavailable

  return (
    <div className="space-y-3">
      <h2 className="text-sm font-semibold uppercase tracking-wider text-muted-foreground">
        Recent Orders
      </h2>
      <div className="rounded-lg border border-border overflow-hidden">
        <Table>
          <TableHeader>
            <TableRow className="bg-muted/50">
              <TableHead className="text-[10px]">Order ID</TableHead>
              <TableHead className="text-[10px]">Created</TableHead>
              <TableHead className="text-[10px]">Pricing</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {orders.map((o) => (
              <TableRow
                key={o.order_id}
                className="cursor-pointer hover:bg-accent/50 transition-colors"
                onClick={() => onSelect(o.order_id)}
              >
                <TableCell className="text-xs font-mono text-primary">{o.order_id}</TableCell>
                <TableCell className="text-xs text-muted-foreground">{new Date(o.created_at).toLocaleString()}</TableCell>
                <TableCell className="text-xs">{o.pricing_status}</TableCell>
              </TableRow>
            ))}
            {orders.length === 0 && (
              <TableRow>
                <TableCell colSpan={3} className="text-xs text-muted-foreground text-center py-4 italic">
                  No recent orders
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </div>
    </div>
  );
}
