import { useState, useCallback } from "react";
import { OrderItem } from "@/lib/types";
import { placeOrder } from "@/lib/api";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Switch } from "@/components/ui/switch";
import { Copy, Plus, Trash2, RefreshCw, Send } from "lucide-react";
import { useToast } from "@/hooks/use-toast";

function generateKey() {
  return crypto.randomUUID();
}

interface Props {
  onOrderPlaced: (orderId: string, eventId?: string) => void;
}

export default function OrderForm({ onOrderPlaced }: Props) {
  const { toast } = useToast();
  const [customerId, setCustomerId] = useState("cust-001");
  const [currency, setCurrency] = useState("USD");
  const [promoCode, setPromoCode] = useState("");
  const [items, setItems] = useState<OrderItem[]>([
    { sku: "WIDGET-A", qty: 2, unit_price_cents: 1500 },
  ]);
  const [idempotencyKey, setIdempotencyKey] = useState(generateKey);
  const [sendDuplicate, setSendDuplicate] = useState(false);
  const [loading, setLoading] = useState(false);
  const [lastOrderId, setLastOrderId] = useState<string | null>(null);
  const [lastEventId, setLastEventId] = useState<string | null>(null);

  const addItem = () => setItems([...items, { sku: "", qty: 1, unit_price_cents: 0 }]);
  const removeItem = (i: number) => setItems(items.filter((_, idx) => idx !== i));
  const updateItem = (i: number, field: keyof OrderItem, value: string | number) => {
    const next = [...items];
    (next[i] as any)[field] = value;
    setItems(next);
  };

  const submit = useCallback(async () => {
    setLoading(true);
    try {
      const res = await placeOrder(
        { customer_id: customerId, currency, items, promo_code: promoCode || undefined },
        idempotencyKey
      );
      setLastOrderId(res.order_id);
      setLastEventId(res.event_id ?? null);
      onOrderPlaced(res.order_id, res.event_id);
      if (!sendDuplicate) setIdempotencyKey(generateKey());
      toast({ title: "Order placed", description: `ID: ${res.order_id}` });
    } catch (e: any) {
      toast({ title: "Error", description: e.message, variant: "destructive" });
    } finally {
      setLoading(false);
    }
  }, [customerId, currency, items, promoCode, idempotencyKey, sendDuplicate, onOrderPlaced, toast]);

  const copyToClipboard = (text: string) => {
    navigator.clipboard.writeText(text);
    toast({ title: "Copied!" });
  };

  return (
    <div className="space-y-4">
      <h2 className="text-sm font-semibold uppercase tracking-wider text-muted-foreground">
        Place Order
      </h2>

      <div className="space-y-3">
        <div>
          <Label className="text-xs text-muted-foreground">Customer ID</Label>
          <Input value={customerId} onChange={(e) => setCustomerId(e.target.value)} className="mt-1 bg-muted text-sm" />
        </div>

        <div>
          <Label className="text-xs text-muted-foreground">Currency</Label>
          <Select value={currency} onValueChange={setCurrency}>
            <SelectTrigger className="mt-1 bg-muted text-sm">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="USD">USD</SelectItem>
              <SelectItem value="INR">INR</SelectItem>
            </SelectContent>
          </Select>
        </div>

        <div>
          <Label className="text-xs text-muted-foreground">Promo Code</Label>
          <Input value={promoCode} onChange={(e) => setPromoCode(e.target.value)} placeholder="optional" className="mt-1 bg-muted text-sm" />
        </div>

        <div>
          <div className="flex items-center justify-between mb-2">
            <Label className="text-xs text-muted-foreground">Items</Label>
            <Button variant="ghost" size="sm" onClick={addItem} className="h-6 text-xs text-primary">
              <Plus className="w-3 h-3 mr-1" /> Add
            </Button>
          </div>
          {items.map((item, i) => (
            <div key={i} className="flex gap-2 mb-2">
              <Input placeholder="SKU" value={item.sku} onChange={(e) => updateItem(i, "sku", e.target.value)} className="bg-muted text-xs flex-1" />
              <Input type="number" placeholder="Qty" value={item.qty} onChange={(e) => updateItem(i, "qty", parseInt(e.target.value) || 0)} className="bg-muted text-xs w-16" />
              <Input type="number" placeholder="¢" value={item.unit_price_cents} onChange={(e) => updateItem(i, "unit_price_cents", parseInt(e.target.value) || 0)} className="bg-muted text-xs w-20" />
              {items.length > 1 && (
                <Button variant="ghost" size="sm" onClick={() => removeItem(i)} className="h-8 w-8 p-0 text-destructive">
                  <Trash2 className="w-3 h-3" />
                </Button>
              )}
            </div>
          ))}
        </div>

        <div className="rounded-md border border-border bg-muted/50 p-3 space-y-2">
          <Label className="text-xs text-muted-foreground">Idempotency Key</Label>
          <div className="flex items-center gap-2">
            <code className="text-[10px] text-primary truncate flex-1">{idempotencyKey}</code>
            <Button variant="ghost" size="sm" onClick={() => setIdempotencyKey(generateKey())} className="h-6 w-6 p-0">
              <RefreshCw className="w-3 h-3" />
            </Button>
          </div>
          <div className="flex items-center gap-2">
            <Switch checked={sendDuplicate} onCheckedChange={setSendDuplicate} />
            <span className="text-xs text-muted-foreground">Send duplicate request</span>
          </div>
        </div>
      </div>

      <div className="flex gap-2">
        <Button onClick={submit} disabled={loading} className="flex-1 text-xs">
          <Send className="w-3 h-3 mr-1" /> {loading ? "Sending…" : "Place Order"}
        </Button>
        <Button onClick={submit} disabled={loading} variant="secondary" className="text-xs">
          <Copy className="w-3 h-3 mr-1" /> Replay
        </Button>
      </div>

      {lastOrderId && (
        <div className="rounded-md border border-border bg-muted/50 p-3 space-y-1">
          <div className="flex items-center justify-between">
            <span className="text-[10px] text-muted-foreground">order_id</span>
            <button onClick={() => copyToClipboard(lastOrderId)} className="text-primary hover:underline text-[10px]">
              <Copy className="w-3 h-3 inline mr-1" />copy
            </button>
          </div>
          <code className="text-xs text-primary block truncate">{lastOrderId}</code>
          {lastEventId && (
            <>
              <div className="flex items-center justify-between mt-2">
                <span className="text-[10px] text-muted-foreground">event_id</span>
                <button onClick={() => copyToClipboard(lastEventId)} className="text-primary hover:underline text-[10px]">
                  <Copy className="w-3 h-3 inline mr-1" />copy
                </button>
              </div>
              <code className="text-xs text-primary block truncate">{lastEventId}</code>
            </>
          )}
        </div>
      )}
    </div>
  );
}
