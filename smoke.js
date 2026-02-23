import http from "k6/http";
import { check, sleep } from "k6";
import { uuidv4 } from "https://jslib.k6.io/k6-utils/1.4.0/index.js";

export const options = {
  vus: 20,
  duration: "30s",
};

export default function () {
  const payload = JSON.stringify({
    customer_id: "cust-001",
    currency: "USD",
    items: [{ sku: "WIDGET-A", qty: 2, unit_price_cents: 1500 }],
  });

  const res = http.post("http://localhost:8080/v1/orders", payload, {
    headers: {
      "Content-Type": "application/json",
      "Idempotency-Key": uuidv4(),
    },
  });

  check(res, {
    "status is 200/201": (r) => r.status === 200 || r.status === 201,
  });

  sleep(0.1);
}
