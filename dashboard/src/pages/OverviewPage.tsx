import { Link } from "react-router-dom";
import { api } from "../api/client";
import { useLoad } from "../api/hooks";
import type { OrderStatus, OverviewView } from "../api/types";
import { Loader, PageHeader, Stat, StatusBadge } from "../components/ui";
import { useApp } from "../context";
import { rupees } from "../format";

const ORDER: OrderStatus[] = [
  "pending_payment", "confirmed", "preparing", "out_for_delivery", "delivered",
  "partially_delivered", "delivery_failed", "cancel_requested", "cancelled", "payment_failed",
];

export default function OverviewPage() {
  const { date, isOwner, kitchen } = useApp();
  const overview = useLoad(() => api.get<OverviewView>(`/admin/overview?date=${date}`), [date]);
  const o = overview.data;

  return (
    <>
      <PageHeader title="Overview">
        <button className="secondary" onClick={overview.reload}>Refresh</button>
      </PageHeader>
      <Loader loading={overview.loading && !o} error={overview.error} onRetry={overview.reload}>
        {o && (
          <>
            {date === kitchen.today && (
              <div className={`notice ${o.orderingOpen ? "ok" : "warn"}`}>
                {o.orderingOpen
                  ? `Ordering is open until ${o.cutoffTime.slice(0, 5)}.`
                  : `Ordering closed at ${o.cutoffTime.slice(0, 5)}. The meal list for today is final.`}
              </div>
            )}
            <div className="stats">
              <Stat label="Meals to prepare" value={o.meals} />
              <Stat label="Confirmed orders" value={(o.statusCounts["confirmed"] ?? 0) + (o.statusCounts["preparing"] ?? 0)} />
              {o.salesPaise != null && <Stat label="Sales (verified)" value={rupees(o.salesPaise)} />}
              {o.pendingRefunds != null && (
                <Stat label="Refunds to review" value={o.pendingRefunds} tone={o.pendingRefunds > 0 ? "warn" : undefined} />
              )}
              <Stat label="Batches without a partner" value={o.unassignedBatches} tone={o.unassignedBatches > 0 ? "warn" : undefined} />
              <Stat label="Delivery issues" value={o.openIssues} tone={o.openIssues > 0 ? "bad" : undefined} />
            </div>

            <div className="grid-2">
              <div className="card">
                <h2>Orders by status</h2>
                {Object.keys(o.statusCounts).length === 0 ? (
                  <p className="muted">No orders for this day.</p>
                ) : (
                  <table>
                    <tbody>
                      {ORDER.filter((s) => o.statusCounts[s]).map((s) => (
                        <tr key={s}>
                          <td><StatusBadge status={s} /></td>
                          <td className="right">{o.statusCounts[s]}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                )}
              </div>
              <div className="card">
                <h2>Next steps</h2>
                <ol className="steps">
                  <li><Link to="/menu">Publish the menu</Link> and set meal capacity.</li>
                  <li>After the cutoff, open <Link to="/kitchen">Kitchen prep</Link> and start preparation.</li>
                  <li>In <Link to="/dispatch">Dispatch</Link>, create batches and assign delivery partners.</li>
                  {isOwner && <li>Review <Link to="/refunds">refunds</Link> and delivery issues.</li>}
                </ol>
              </div>
            </div>
          </>
        )}
      </Loader>
    </>
  );
}
