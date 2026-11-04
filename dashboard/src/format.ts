import type { OrderStatus } from "./api/types";

export const rupees = (paise: number) =>
  "₹" + (paise / 100).toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 });

/** Rupee input value ("120" or "120.50") to paise; null if invalid. */
export function toPaise(value: string): number | null {
  const n = Number(value);
  if (!Number.isFinite(n) || n < 0) return null;
  return Math.round(n * 100);
}

export const dateTime = (iso: string) =>
  new Date(iso).toLocaleString("en-IN", { day: "numeric", month: "short", hour: "numeric", minute: "2-digit" });

export const prettyDate = (isoDate: string) =>
  new Date(isoDate + "T00:00:00").toLocaleDateString("en-IN", { weekday: "short", day: "numeric", month: "short", year: "numeric" });

export const statusLabels: Record<OrderStatus, string> = {
  pending_payment: "Awaiting payment",
  confirmed: "Confirmed",
  preparing: "Preparing",
  out_for_delivery: "Out for delivery",
  delivered: "Delivered",
  partially_delivered: "Partially delivered",
  delivery_failed: "Delivery failed",
  cancel_requested: "Cancel requested",
  cancelled: "Cancelled",
  payment_failed: "Payment failed",
};

export const statusTone: Record<OrderStatus, "ok" | "warn" | "bad" | "muted"> = {
  pending_payment: "muted",
  confirmed: "ok",
  preparing: "ok",
  out_for_delivery: "ok",
  delivered: "ok",
  partially_delivered: "warn",
  delivery_failed: "bad",
  cancel_requested: "warn",
  cancelled: "muted",
  payment_failed: "muted",
};

export const plural = (n: number, word: string) => `${n} ${word}${n === 1 ? "" : "s"}`;

export const label =(s: string) => s.replace(/_/g, " ").replace(/^\w/, (c) => c.toUpperCase());
