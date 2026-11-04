import { useMemo, useState } from "react";
import { api } from "../api/client";
import { useAction, useLoad } from "../api/hooks";
import type { AdminOrder, OrderStatus } from "../api/types";
import { Badge, Loader, PageHeader, StatusBadge } from "../components/ui";
import { useApp } from "../context";
import { dateTime, label, rupees, statusLabels } from "../format";

export default function OrdersPage() {
  const { date } = useApp();
  const [status, setStatus] = useState<OrderStatus | "">("");
  const [search, setSearch] = useState("");
  const orders = useLoad(
    () => api.get<AdminOrder[]>(`/admin/orders?date=${date}${status ? `&status=${status}` : ""}`),
    [date, status],
  );
  const download = useAction();
  const refund = useAction();

  const shown = useMemo(() => {
    const q = search.trim().toLowerCase();
    if (!q) return orders.data ?? [];
    return (orders.data ?? []).filter((o) =>
      [o.customerName, o.customerPhone, o.officeName, o.id, o.paymentReference ?? ""].some((v) => v.toLowerCase().includes(q)),
    );
  }, [orders.data, search]);

  async function requestRefund(o: AdminOrder) {
    if (!confirm(`Start a refund of ${rupees(o.totalPaise)} for ${o.customerName}? You approve it on the Refunds page.`)) return;
    const updated = await refund.run(() => api.post<AdminOrder>(`/admin/orders/${o.id}/refund`));
    if (updated) orders.setData((orders.data ?? []).map((x) => (x.id === updated.id ? updated : x)));
  }

  return (
    <>
      <PageHeader title="Orders">
        <input placeholder="Search name, phone, office…" value={search} onChange={(e) => setSearch(e.target.value)} />
        <select value={status} onChange={(e) => setStatus(e.target.value as OrderStatus | "")}>
          <option value="">All statuses</option>
          {Object.entries(statusLabels).map(([k, v]) => (
            <option key={k} value={k}>{v}</option>
          ))}
        </select>
        <button
          className="secondary"
          disabled={download.busy}
          onClick={() => download.run(() => api.download(`/admin/exports/orders.csv?from=${date}&to=${date}`, `orders-${date}.csv`))}
        >
          Export CSV
        </button>
      </PageHeader>
      {(download.error || refund.error) && <div className="notice bad">{download.error ?? refund.error}</div>}
      <Loader loading={orders.loading && !orders.data} error={orders.error} onRetry={orders.reload}>
        <p className="muted small">{shown.length} order{shown.length === 1 ? "" : "s"}</p>
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Placed</th><th>Customer</th><th>Deliver to</th><th>Meals</th>
                <th className="right">Total</th><th>Status</th><th>Payment</th><th></th>
              </tr>
            </thead>
            <tbody>
              {shown.map((o) => (
                <tr key={o.id}>
                  <td className="nowrap">{dateTime(o.createdAt)}</td>
                  <td>{o.customerName}<div className="muted small">{o.customerPhone}</div></td>
                  <td>{o.officeName}{o.dropPointName && <div className="muted small">{o.dropPointName}</div>}</td>
                  <td>{o.lines.map((l) => `${l.quantity} × ${l.name}`).join(", ")}</td>
                  <td className="right nowrap">{rupees(o.totalPaise)}</td>
                  <td><StatusBadge status={o.status} /></td>
                  <td>
                    {o.paymentStatus && <Badge tone={o.paymentStatus === "verified" ? "ok" : "muted"}>{label(o.paymentStatus)}</Badge>}
                    {o.refundStatus && <div className="small muted">Refund {o.refundStatus}</div>}
                    {o.paymentReference && <div className="small muted">{o.paymentReference}</div>}
                  </td>
                  <td>
                    {o.paymentStatus === "verified" && !o.refundStatus && (
                      <button className="secondary small" disabled={refund.busy} onClick={() => requestRefund(o)}>Refund</button>
                    )}
                  </td>
                </tr>
              ))}
              {shown.length === 0 && (
                <tr><td colSpan={8} className="muted">No orders.</td></tr>
              )}
            </tbody>
          </table>
        </div>
      </Loader>
    </>
  );
}
