import { useEffect, useState } from "react";
import type { OrderStatus } from "./api/types";

/** ₹1,250 or ₹99.50: no ".00" on whole amounts, Indian digit grouping. */
export const rupees = (paise: number) => {
  const whole = paise % 100 === 0;
  return "₹" + (paise / 100).toLocaleString("en-IN", { minimumFractionDigits: whole ? 0 : 2, maximumFractionDigits: 2 });
};

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

/** Seconds of the day "now" in the given IANA timezone. */
function secondsNowIn(timezone: string): number {
  const parts = new Intl.DateTimeFormat("en-GB", { timeZone: timezone, hour: "2-digit", minute: "2-digit", second: "2-digit", hour12: false })
    .formatToParts(new Date());
  const get = (t: string) => Number(parts.find((p) => p.type === t)?.value ?? 0);
  return (get("hour") % 24) * 3600 + get("minute") * 60 + get("second");
}

/** Live seconds left until today's cutoff ("HH:mm[:ss]") in the kitchen's timezone; ticks every second. */
export function useCutoffCountdown(cutoff: string, timezone: string): number | null {
  const calc = () => {
    const [h, m, sec] = cutoff.split(":").map(Number);
    if (Number.isNaN(h)) return null;
    return h * 3600 + (m || 0) * 60 + (sec || 0) - secondsNowIn(timezone);
  };
  const [left, setLeft] = useState(calc);
  useEffect(() => {
    const id = setInterval(() => setLeft(calc()), 1000);
    return () => clearInterval(id);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [cutoff, timezone]);
  return left;
}

export function formatLeft(seconds: number): string {
  const h = Math.floor(seconds / 3600);
  const m = Math.floor((seconds % 3600) / 60);
  const s = Math.floor(seconds % 60);
  if (h > 0) return `${h}h ${m}m`;
  if (m >= 10) return `${m}m`;
  return `${m}:${String(s).padStart(2, "0")}`;
}

export function greeting(): string {
  const h = new Date().getHours();
  return h < 12 ? "Good morning" : h < 17 ? "Good afternoon" : "Good evening";
}
