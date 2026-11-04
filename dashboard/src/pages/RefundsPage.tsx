import { useState } from "react";
import { api } from "../api/client";
import { useAction, useLoad } from "../api/hooks";
import type { AdminOrder } from "../api/types";
import { Badge, Loader, PageHeader, StatusBadge } from "../components/ui";
import { dateTime, rupees } from "../format";

export default function RefundsPage() {
  const [all, setAll] = useState(false);
  const refunds = useLoad(() => api.get<AdminOrder[]>(`/admin/refunds${all ? "?all=true" : ""}`), [all]);
  const decide = useAction();

  async function decideRefund(o: AdminOrder, approve: boolean) {
    const note = prompt(approve ? `Approve a refund of ${rupees(o.totalPaise)} to ${o.customerName}? Optional note:` : `Reject the refund for ${o.customerName}? Reason:`);
    if (note === null) return;
    const updated = await decide.run(() =>
      api.post<AdminOrder>(`/admin/refunds/${o.id}/${approve ? "approve" : "reject"}`, { note: note || null }),
    );
    if (updated) {
      refunds.setData(all ? (refunds.data ?? []).map((x) => (x.id === updated.id ? updated : x)) : (refunds.data ?? []).filter((x) => x.id !== updated.id));
    }
  }

  return (
    <>
      <PageHeader title="Refunds">
        <label className="inline">
          <input type="checkbox" checked={all} onChange={(e) => setAll(e.target.checked)} /> Show completed and rejected
        </label>
      </PageHeader>
      <p className="muted">
        Refunds come from customer cancellations before the cutoff, payments that arrived after a meal sold out, and
        refunds you start from the Orders page. Approving sends the refund to the payment gateway.
        Rejecting a cancellation puts the order back to confirmed.
      </p>
      {decide.error && <div className="notice bad">{decide.error}</div>}
      <Loader loading={refunds.loading && !refunds.data} error={refunds.error} onRetry={refunds.reload}>
        <div className="table-wrap">
          <table>
            <thead>
              <tr><th>Order date</th><th>Customer</th><th>Meals</th><th className="right">Amount</th><th>Order</th><th>Refund</th><th></th></tr>
            </thead>
            <tbody>
              {(refunds.data ?? []).map((o) => (
                <tr key={o.id}>
                  <td className="nowrap">{o.deliveryDate}<div className="muted small">placed {dateTime(o.createdAt)}</div></td>
                  <td>{o.customerName}<div className="muted small">{o.customerPhone}</div></td>
                  <td>{o.lines.map((l) => `${l.quantity} × ${l.name}`).join(", ")}</td>
                  <td className="right nowrap">{rupees(o.totalPaise)}</td>
                  <td><StatusBadge status={o.status} /></td>
                  <td>
                    <Badge tone={o.refundStatus === "requested" ? "warn" : o.refundStatus === "completed" ? "ok" : "muted"}>{o.refundStatus}</Badge>
                    <div className="muted small">{o.paymentReference}</div>
                  </td>
                  <td className="nowrap">
                    {o.refundStatus === "requested" && (
                      <div className="row">
                        <button className="small" disabled={decide.busy} onClick={() => decideRefund(o, true)}>Approve</button>
                        <button className="secondary small" disabled={decide.busy} onClick={() => decideRefund(o, false)}>Reject</button>
                      </div>
                    )}
                  </td>
                </tr>
              ))}
              {(refunds.data ?? []).length === 0 && <tr><td colSpan={7} className="muted">Nothing waiting for a decision.</td></tr>}
            </tbody>
          </table>
        </div>
      </Loader>
    </>
  );
}
