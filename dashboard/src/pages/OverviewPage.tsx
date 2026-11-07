import { Link } from "react-router-dom";
import { api } from "../api/client";
import { useLoad } from "../api/hooks";
import type { OrderStatus, OverviewView } from "../api/types";
import { Icon } from "../components/icons";
import { Loader, Stat } from "../components/ui";
import { useApp } from "../context";
import { formatLeft, greeting, prettyDate, rupees, statusLabels, useCutoffCountdown } from "../format";

const ORDER: OrderStatus[] = [
  "pending_payment", "confirmed", "preparing", "out_for_delivery", "delivered",
  "partially_delivered", "delivery_failed", "cancel_requested", "cancelled", "payment_failed",
];

const COLORS: Record<OrderStatus, string> = {
  pending_payment: "#e7c9a0",
  confirmed: "#1f5f3f",
  preparing: "#2e8b57",
  out_for_delivery: "#f2994a",
  delivered: "#6fcf97",
  partially_delivered: "#d9a400",
  delivery_failed: "#b3261e",
  cancel_requested: "#e59866",
  cancelled: "#b8a893",
  payment_failed: "#d8c5ac",
};

export default function OverviewPage() {
  const { date, isOwner, kitchen, me } = useApp();
  const overview = useLoad(() => api.get<OverviewView>(`/admin/overview?date=${date}`), [date]);
  const o = overview.data;
  const isToday = date === kitchen.today;
  const first = (me.name || "").trim().split(/\s+/)[0];

  return (
    <>
      <div className="welcome">
        <span className="float" style={{ left: "38%", bottom: -8 }}>🍛</span>
        <span className="float" style={{ right: 40, bottom: -6, animationDelay: "1s" }}>🥗</span>
        <div className="grow">
          <p>{greeting()}{first ? "," : ""}</p>
          <h1>{first ? `${first} 👋` : "Welcome back"}</h1>
          <p>{isToday ? "Here's how today's lunch is shaping up." : `Looking back at ${prettyDate(date)}.`}</p>
        </div>
        {isToday && o && <Countdown open={o.orderingOpen} cutoff={kitchen.cutoffTime} timezone={kitchen.timezone} />}
        <button className="secondary" onClick={overview.reload} title="Refresh">
          <Icon name="refresh" size={18} /> Refresh
        </button>
      </div>

      <Loader loading={overview.loading && !o} error={overview.error} onRetry={overview.reload}>
        {o && (
          <>
            <div className="stats">
              <Stat hero icon="bowl" label="Meals to prepare" value={o.meals} />
              <Stat icon="check" tone="ok" label="Confirmed orders" value={(o.statusCounts["confirmed"] ?? 0) + (o.statusCounts["preparing"] ?? 0)} />
              {o.salesPaise != null && <Stat icon="rupee" label="Sales (verified)" value={rupees(o.salesPaise)} />}
              {o.pendingRefunds != null && (
                <Stat icon="refund" label="Refunds to review" value={o.pendingRefunds} tone={o.pendingRefunds > 0 ? "warn" : undefined} />
              )}
              <Stat icon="truck" label="Batches without a partner" value={o.unassignedBatches} tone={o.unassignedBatches > 0 ? "warn" : undefined} />
              <Stat icon="alert" label="Delivery issues" value={o.openIssues} tone={o.openIssues > 0 ? "bad" : undefined} />
            </div>

            <div className="grid-2">
              <div className="card">
                <h2>Orders by status</h2>
                <StatusBreakdown counts={o.statusCounts} />
              </div>
              <div className="card">
                <h2>Today's flow</h2>
                <ol className="steps">
                  <li><span><Link to="/menu">Publish the menu</Link> and set meal capacity.</span></li>
                  <li><span>After the cutoff, open <Link to="/kitchen">Kitchen prep</Link> and start preparation.</span></li>
                  <li><span>In <Link to="/dispatch">Dispatch</Link>, create batches and assign delivery partners.</span></li>
                  {isOwner && <li><span>Review <Link to="/refunds">refunds</Link> and delivery issues.</span></li>}
                </ol>
              </div>
            </div>
          </>
        )}
      </Loader>
    </>
  );
}

function Countdown({ open, cutoff, timezone }: { open: boolean; cutoff: string; timezone: string }) {
  const left = useCutoffCountdown(cutoff, timezone);
  // The server decides whether ordering is open; the countdown is only shown when this clock agrees.
  if (!open || left == null || left <= 0) {
    return (
      <div className="countdown">
        <div className="small" style={{ opacity: 0.75 }}>Ordering</div>
        <div className="big">{open ? "Open" : "Closed"}</div>
        <div className="small" style={{ opacity: 0.75 }}>
          {open ? `Cutoff at ${cutoff.slice(0, 5)}` : `The meal list is final (cutoff ${cutoff.slice(0, 5)})`}
        </div>
      </div>
    );
  }
  const fraction = Math.max(0, Math.min(1, left / (4 * 3600)));
  return (
    <div className="countdown">
      <div className="small" style={{ opacity: 0.75 }}>Ordering closes in</div>
      <div className="big">{formatLeft(left)}</div>
      <div className="bar"><span style={{ width: `${fraction * 100}%` }} /></div>
      <div className="small" style={{ opacity: 0.75, marginTop: 6 }}>Cutoff at {cutoff.slice(0, 5)}</div>
    </div>
  );
}

function StatusBreakdown({ counts }: { counts: Partial<Record<OrderStatus, number>> }) {
  const rows = ORDER.filter((s) => counts[s]);
  const total = rows.reduce((sum, s) => sum + (counts[s] ?? 0), 0);
  if (total === 0) {
    return (
      <div className="muted" style={{ textAlign: "center", padding: "24px 0" }}>
        <div style={{ fontSize: "2.4rem" }}>🥡</div>
        No orders for this day yet.
      </div>
    );
  }
  return (
    <>
      <div className="dist">
        {rows.map((s) => (
          <span key={s} title={`${statusLabels[s]}: ${counts[s]}`} style={{ flexGrow: counts[s], background: COLORS[s] }} />
        ))}
      </div>
      <div className="legend">
        {rows.map((s) => (
          <div className="legend-row" key={s}>
            <span className="dot" style={{ background: COLORS[s] }} />
            <span>{statusLabels[s]}</span>
            <span className="muted small">{Math.round(((counts[s] ?? 0) / total) * 100)}%</span>
            <strong>{counts[s]}</strong>
          </div>
        ))}
      </div>
    </>
  );
}
