import { api } from "../api/client";
import { useAction, useLoad } from "../api/hooks";
import type { CountResult, PrepView } from "../api/types";
import { Loader, PageHeader, Stat, StatusBadge } from "../components/ui";
import { useApp } from "../context";
import { plural, prettyDate } from "../format";

export default function KitchenPage() {
  const { date } = useApp();
  const prep = useLoad(() => api.get<PrepView>(`/admin/prep?date=${date}`), [date]);
  const start = useAction();
  const p = prep.data;

  async function startPrep() {
    if (!confirm("Move all confirmed orders to 'Preparing'? Customers can no longer cancel them.")) return;
    const r = await start.run(() => api.post<CountResult>("/admin/prep/start", { date }));
    if (r) prep.reload();
  }

  return (
    <>
      <PageHeader title="Kitchen prep">
        <button className="secondary" onClick={prep.reload}>Refresh</button>
        <button className="secondary" onClick={() => window.print()}>Print packing lists</button>
        <button onClick={startPrep} disabled={!p || !p.cutoffPassed || p.awaitingPrep === 0 || start.busy}>
          Start preparation{p && p.awaitingPrep > 0 ? ` (${p.awaitingPrep})` : ""}
        </button>
      </PageHeader>
      {start.error && <div className="notice bad">{start.error}</div>}
      <Loader loading={prep.loading && !p} error={prep.error} onRetry={prep.reload}>
        {p && (
          <>
            {!p.cutoffPassed && (
              <div className="notice warn no-print">
                Ordering is still open, so these totals can change until the cutoff.
              </div>
            )}
            {p.pendingCancellations > 0 && (
              <div className="notice warn no-print">
                {p.pendingCancellations} cancellation request{p.pendingCancellations > 1 ? "s are" : " is"} waiting for a refund
                decision and {p.pendingCancellations > 1 ? "are" : "is"} not counted below.
              </div>
            )}

            <div className="stats no-print">
              <Stat label="Meals" value={p.totalMeals} />
              <Stat label="Orders" value={p.confirmedOrders} />
              <Stat label="Waiting to start" value={p.awaitingPrep} />
              <Stat label="Drop points" value={p.groups.length} />
            </div>

            <div className="card print-break">
              <h2>Meal totals · {prettyDate(p.date)}</h2>
              {p.mealTotals.length === 0 ? (
                <p className="muted">No confirmed orders.</p>
              ) : (
                <table>
                  <tbody>
                    {p.mealTotals.map((m) => (
                      <tr key={m.menuItemId}>
                        <td>{m.name}</td>
                        <td className="right"><strong>{m.quantity}</strong></td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </div>

            {p.groups.map((g) => (
              <div className="card packing-group" key={`${g.officeId}-${g.dropPointId ?? ""}`}>
                <h3>
                  {g.officeName}
                  {g.dropPointName && <span className="muted">· {g.dropPointName}</span>}
                </h3>
                <p className="muted small">{g.officeAddress}</p>
                <p>
                  <strong>{plural(g.mealTotals.reduce((n, m) => n + m.quantity, 0), "meal")}:</strong>{" "}
                  {g.mealTotals.map((m) => `${m.quantity} × ${m.name}`).join(", ")}
                </p>
                <table>
                  <thead>
                    <tr><th>Customer</th><th>Meals</th><th className="no-print">Status</th><th>Packed</th></tr>
                  </thead>
                  <tbody>
                    {g.orders.map((o) => (
                      <tr key={o.orderId}>
                        <td>{o.customerName}<div className="muted small">#{o.orderId.slice(0, 8)}</div></td>
                        <td>{o.lines.map((l) => `${l.quantity} × ${l.name}`).join(", ")}</td>
                        <td className="no-print"><StatusBadge status={o.status} /></td>
                        <td>☐</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            ))}
          </>
        )}
      </Loader>
    </>
  );
}
