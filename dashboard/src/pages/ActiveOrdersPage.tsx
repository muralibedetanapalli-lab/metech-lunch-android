import { useEffect, useMemo, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { api } from "../api/client";
import { useLoad } from "../api/hooks";
import type { AdminOrder, OrderStatus, OrderTracking } from "../api/types";
import { Icon } from "../components/icons";
import { LiveMap } from "../components/LiveMap";
import { Loader, PageHeader, StatusBadge } from "../components/ui";
import { useApp } from "../context";
import { plural, rupees } from "../format";

const ACTIVE: OrderStatus[] = ["out_for_delivery", "preparing", "confirmed"];
type Filter = "all" | OrderStatus;

const FILTERS: [Filter, string][] = [
  ["all", "All active"],
  ["out_for_delivery", "🛵 On the way"],
  ["preparing", "👩‍🍳 Preparing"],
  ["confirmed", "✅ Confirmed"],
];

export default function ActiveOrdersPage() {
  const { date } = useApp();
  const [params, setParams] = useSearchParams();
  const selectedId = params.get("order");
  const [filter, setFilter] = useState<Filter>("all");
  const [query, setQuery] = useState("");

  const orders = useLoad(() => api.get<AdminOrder[]>(`/admin/orders?date=${date}`), [date]);
  usePoll(orders.reload, 15_000);

  const active = useMemo(
    () =>
      (orders.data ?? [])
        .filter((o) => ACTIVE.includes(o.status))
        .sort((a, b) => ACTIVE.indexOf(a.status) - ACTIVE.indexOf(b.status)),
    [orders.data],
  );
  const shown = active.filter(
    (o) =>
      (filter === "all" || o.status === filter) &&
      (query.trim() === "" ||
        `${o.customerName} ${o.customerPhone} ${o.officeName} ${o.id}`.toLowerCase().includes(query.trim().toLowerCase())),
  );
  const count = (s: Filter) => (s === "all" ? active.length : active.filter((o) => o.status === s).length);
  const select = (id: string | null) => setParams(id ? { order: id } : {}, { replace: true });

  return (
    <>
      <PageHeader title="Active orders" subtitle="Pick an order to see where it is. Orders on the way show the delivery partner live on the map.">
        <button className="secondary" onClick={orders.reload}>
          <Icon name="refresh" size={18} /> Refresh
        </button>
      </PageHeader>

      <Loader loading={orders.loading && !orders.data} error={orders.error} onRetry={orders.reload}>
        <div className="tracking-layout">
          <section className="tracking-list">
            <div className="tracking-filters">
              {FILTERS.map(([f, label]) => (
                <button key={f} className={`pill ${filter === f ? "active" : ""}`} onClick={() => setFilter(f)}>
                  {label} <span className="pill-count">{count(f)}</span>
                </button>
              ))}
            </div>
            <label className="search">
              <Icon name="search" size={18} />
              <input placeholder="Search customer, phone or office" value={query} onChange={(e) => setQuery(e.target.value)} />
            </label>

            {shown.length === 0 ? (
              <div className="card muted" style={{ textAlign: "center", padding: 32 }}>
                <div style={{ fontSize: "2.4rem" }}>🥡</div>
                {active.length === 0 ? "No active orders for this day." : "Nothing matches this filter."}
              </div>
            ) : (
              <div className="order-cards">
                {shown.map((o) => (
                  <button key={o.id} className={`order-card ${o.id === selectedId ? "selected" : ""}`} onClick={() => select(o.id)}>
                    <span className="order-emoji">{emojiFor(o.status)}</span>
                    <span className="order-main">
                      <strong>{o.customerName}</strong>
                      <span className="muted small">
                        {o.officeName}
                        {o.dropPointName ? ` · ${o.dropPointName}` : ""} · {plural(o.lines.reduce((n, l) => n + l.quantity, 0), "meal")}
                      </span>
                    </span>
                    <span className="order-side">
                      <StatusBadge status={o.status} />
                      {o.status === "out_for_delivery" && <span className="live-tag"><span className="pulse">●</span> Live</span>}
                    </span>
                  </button>
                ))}
              </div>
            )}
          </section>

          <section className="tracking-detail">
            {selectedId ? (
              <OrderDetail key={selectedId} orderId={selectedId} onClose={() => select(null)} />
            ) : (
              <div className="card tracking-empty">
                <div style={{ fontSize: "3rem" }}>🗺️</div>
                <h2>Select an order</h2>
                <p className="muted">Orders that are on the way show the delivery partner's live location here.</p>
              </div>
            )}
          </section>
        </div>
      </Loader>
    </>
  );
}

function OrderDetail({ orderId, onClose }: { orderId: string; onClose: () => void }) {
  const t = useLoad(() => api.get<OrderTracking>(`/admin/orders/${orderId}/tracking`), [orderId]);
  const live = t.data?.live ?? false;
  usePoll(t.reload, live ? 5_000 : 20_000);
  const data = t.data;

  if (t.error) return <div className="notice bad">{t.error}</div>;
  if (!data) return <div className="skeleton" style={{ height: 420 }} />;
  const { order, batch, location, trail } = data;

  return (
    <div className="card tracking-card">
      <div className="row" style={{ alignItems: "flex-start" }}>
        <div className="grow">
          <div className="muted small">Order #{order.id.slice(0, 6).toUpperCase()}</div>
          <h2 style={{ margin: "2px 0 6px" }}>{order.customerName}</h2>
          <div className="row">
            <StatusBadge status={order.status} />
            <span className="muted small">
              {order.officeName}
              {order.dropPointName ? ` · ${order.dropPointName}` : ""}
            </span>
          </div>
        </div>
        <button className="secondary small" onClick={onClose} aria-label="Close">✕</button>
      </div>

      <Steps status={order.status} />

      {live ? (
        location ? (
          <>
            <LiveMap location={location} trail={trail} />
            <div className="map-facts">
              <Fact icon="clock" label="Last update"><Ago iso={location.recordedAt} /></Fact>
              <Fact icon="truck" label="Speed">{location.speedMps != null ? `${Math.round(location.speedMps * 3.6)} km/h` : "—"}</Fact>
              <Fact icon="target" label="Accuracy">{location.accuracyMeters != null ? `± ${Math.round(location.accuracyMeters)} m` : "—"}</Fact>
            </div>
          </>
        ) : (
          <div className="map-waiting">
            <div className="pulse" style={{ fontSize: "2.4rem" }}>📡</div>
            <strong>Waiting for the partner's location…</strong>
            <span className="muted small">It appears once their phone sends its first GPS fix (location must be on in the partner app).</span>
          </div>
        )
      ) : (
        <div className="map-waiting">
          <div style={{ fontSize: "2.4rem" }}>{emojiFor(order.status)}</div>
          <strong>{notLiveTitle(order.status)}</strong>
          <span className="muted small">{notLiveHelp(order.status)}</span>
        </div>
      )}

      <div className="detail-grid">
        <div>
          <h3>Delivery partner</h3>
          {batch?.partnerName ? (
            <div className="partner">
              <div className="avatar">{batch.partnerName.charAt(0).toUpperCase()}</div>
              <div className="grow">
                <strong>{batch.partnerName}</strong>
                <div className="muted small">
                  {batch.deliveredOrders} of {plural(batch.totalOrders, "order")} in this batch handed over
                </div>
              </div>
              {batch.partnerPhone && (
                <a className="call" href={`tel:${batch.partnerPhone}`} title={`Call ${batch.partnerName}`}>
                  <Icon name="phone" size={18} />
                </a>
              )}
            </div>
          ) : (
            <p className="muted">{batch ? "No partner assigned yet." : "Not in a delivery batch yet."}</p>
          )}
        </div>
        <div>
          <h3>Meals</h3>
          {order.lines.map((l) => (
            <div key={l.name} className="row small" style={{ justifyContent: "space-between" }}>
              <span>{l.quantity} × {l.name}</span>
            </div>
          ))}
          <div className="row" style={{ justifyContent: "space-between", marginTop: 6 }}>
            <span className="muted small">Total</span>
            <strong>{rupees(order.totalPaise)}</strong>
          </div>
          <div className="muted small" style={{ marginTop: 4 }}>📞 {order.customerPhone}</div>
        </div>
      </div>
    </div>
  );
}

const STEP_LABELS = ["Confirmed", "Preparing", "On the way", "Delivered"];
function Steps({ status }: { status: OrderStatus }) {
  const current = ({ confirmed: 0, preparing: 1, out_for_delivery: 2, delivered: 3 } as Partial<Record<OrderStatus, number>>)[status] ?? -1;
  if (current < 0) return null;
  return (
    <ol className="order-steps">
      {STEP_LABELS.map((label, i) => (
        <li key={label} className={i < current ? "done" : i === current ? "current" : ""}>
          <span className="step-dot">{i < current ? "✓" : i + 1}</span>
          <span>{label}</span>
        </li>
      ))}
    </ol>
  );
}

function Fact({ icon, label, children }: { icon: "clock" | "truck" | "target"; label: string; children: React.ReactNode }) {
  return (
    <div className="fact">
      <Icon name={icon} size={18} />
      <div>
        <div className="muted small">{label}</div>
        <strong>{children}</strong>
      </div>
    </div>
  );
}

/** "12s ago", ticking every second; amber once the last fix is over a minute old. */
function Ago({ iso }: { iso: string }) {
  const [now, setNow] = useState(Date.now());
  useEffect(() => {
    const id = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(id);
  }, []);
  const s = Math.max(0, Math.round((now - new Date(iso).getTime()) / 1000));
  const text = s < 60 ? `${s}s ago` : s < 3600 ? `${Math.floor(s / 60)} min ago` : `${Math.floor(s / 3600)} h ago`;
  return <span style={{ color: s > 60 ? "var(--warn)" : undefined }}>{text}</span>;
}

function usePoll(fn: () => void, ms: number) {
  useEffect(() => {
    const id = setInterval(() => {
      if (document.visibilityState === "visible") fn();
    }, ms);
    return () => clearInterval(id);
  }, [fn, ms]);
}

function emojiFor(s: OrderStatus) {
  return s === "out_for_delivery" ? "🛵" : s === "preparing" ? "👩‍🍳" : s === "confirmed" ? "✅" : s === "delivered" ? "🎉" : "📦";
}

function notLiveTitle(s: OrderStatus) {
  if (s === "delivered") return "Delivered";
  if (s === "confirmed" || s === "preparing") return "Not picked up yet";
  return "Not on the road";
}

function notLiveHelp(s: OrderStatus) {
  if (s === "delivered") return "This order has been handed over.";
  if (s === "confirmed" || s === "preparing") return "The live map appears once the delivery partner confirms pickup.";
  return "Live tracking is only shown while the meals are on the way.";
}
