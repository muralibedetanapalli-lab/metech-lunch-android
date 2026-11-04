import { useState } from "react";
import { api } from "../api/client";
import { useAction, useLoad } from "../api/hooks";
import type { OrderStatus, ReportView } from "../api/types";
import { Loader, PageHeader, Stat, StatusBadge } from "../components/ui";
import { useApp } from "../context";
import { prettyDate, rupees } from "../format";

function minusDays(iso: string, days: number) {
  const d = new Date(iso + "T00:00:00Z");
  d.setUTCDate(d.getUTCDate() - days);
  return d.toISOString().slice(0, 10);
}

export default function ReportsPage() {
  const { kitchen } = useApp();
  const [from, setFrom] = useState(minusDays(kitchen.today, 6));
  const [to, setTo] = useState(kitchen.today);
  const report = useLoad(() => api.get<ReportView>(`/admin/reports?from=${from}&to=${to}`), [from, to]);
  const download = useAction();
  const r = report.data;
  const maxMeals = Math.max(1, ...(r?.days.map((d) => d.meals) ?? [1]));

  return (
    <>
      <PageHeader title="Reports">
        <label className="inline">From <input type="date" value={from} max={to} onChange={(e) => e.target.value && setFrom(e.target.value)} /></label>
        <label className="inline">To <input type="date" value={to} min={from} onChange={(e) => e.target.value && setTo(e.target.value)} /></label>
        <button
          className="secondary"
          disabled={download.busy}
          onClick={() => download.run(() => api.download(`/admin/exports/orders.csv?from=${from}&to=${to}`, `orders-${from}-to-${to}.csv`))}
        >
          Export orders (CSV)
        </button>
      </PageHeader>
      {download.error && <div className="notice bad">{download.error}</div>}
      <Loader loading={report.loading && !r} error={report.error} onRetry={report.reload}>
        {r && (
          <>
            <div className="stats">
              <Stat label="Sales (verified payments)" value={rupees(r.totalSalesPaise)} />
              <Stat label="Refunded" value={rupees(r.totalRefundedPaise)} />
              <Stat label="Net" value={rupees(r.totalSalesPaise - r.totalRefundedPaise)} />
              <Stat label="Meals" value={r.days.reduce((n, d) => n + d.meals, 0)} />
              <Stat label="Refunds awaiting decision" value={r.pendingRefunds} tone={r.pendingRefunds > 0 ? "warn" : undefined} />
            </div>

            <div className="card">
              <h2>By day</h2>
              <div className="table-wrap" style={{ border: 0 }}>
                <table>
                  <thead><tr><th>Day</th><th className="right">Orders</th><th>Meals</th><th className="right">Sales</th><th className="right">Refunded</th></tr></thead>
                  <tbody>
                    {r.days.map((d) => (
                      <tr key={d.date}>
                        <td className="nowrap">{prettyDate(d.date)}</td>
                        <td className="right">{d.orders}</td>
                        <td style={{ minWidth: 160 }}>
                          <div className="row" style={{ flexWrap: "nowrap" }}>
                            <div style={{ height: 8, borderRadius: 4, background: "var(--primary)", width: `${(d.meals / maxMeals) * 100}%`, minWidth: d.meals ? 4 : 0 }} />
                            <span className="small">{d.meals}</span>
                          </div>
                        </td>
                        <td className="right nowrap">{rupees(d.salesPaise)}</td>
                        <td className="right nowrap">{d.refundedPaise ? rupees(d.refundedPaise) : "—"}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>

            <div className="grid-2" style={{ marginTop: 16 }}>
              <div className="card">
                <h2>Meals</h2>
                <table><tbody>
                  {r.mealTotals.map((m) => <tr key={m.menuItemId}><td>{m.name}</td><td className="right">{m.quantity}</td></tr>)}
                  {r.mealTotals.length === 0 && <tr><td className="muted">No meals in this range.</td></tr>}
                </tbody></table>
              </div>
              <div className="card">
                <h2>Meals by office</h2>
                <table><tbody>
                  {Object.entries(r.officeTotals).sort((a, b) => b[1] - a[1]).map(([office, n]) => (
                    <tr key={office}><td>{office}</td><td className="right">{n}</td></tr>
                  ))}
                </tbody></table>
              </div>
              <div className="card">
                <h2>Orders by status</h2>
                <table><tbody>
                  {Object.entries(r.statusCounts).map(([s, n]) => (
                    <tr key={s}><td><StatusBadge status={s as OrderStatus} /></td><td className="right">{n}</td></tr>
                  ))}
                </tbody></table>
              </div>
            </div>
          </>
        )}
      </Loader>
    </>
  );
}
