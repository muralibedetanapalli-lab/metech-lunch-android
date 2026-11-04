import { useState } from "react";
import { api } from "../api/client";
import { useAction, useLoad } from "../api/hooks";
import type { AdminBatch, AdminIssue, AdminUser, BatchStatus } from "../api/types";
import { Badge, Loader, PageHeader, StatusBadge } from "../components/ui";
import { useApp } from "../context";
import { dateTime, label, plural } from "../format";

const batchTone: Record<BatchStatus, "ok" | "warn" | "bad" | "muted"> = {
  unassigned: "warn",
  assigned: "muted",
  picked_up: "ok",
  in_transit: "ok",
  delivered: "ok",
  partially_delivered: "warn",
  failed: "bad",
};

export default function DispatchPage() {
  const { date } = useApp();
  const batches = useLoad(() => api.get<AdminBatch[]>(`/admin/batches?date=${date}`), [date]);
  const issues = useLoad(() => api.get<AdminIssue[]>(`/admin/issues?date=${date}`), [date]);
  const partners = useLoad(() => api.get<AdminUser[]>("/admin/partners"), []);
  const generate = useAction();

  async function createBatches() {
    const result = await generate.run(() => api.post<AdminBatch[]>("/admin/batches/generate", { date }));
    if (result) batches.setData(result);
  }

  function replace(updated: AdminBatch) {
    batches.setData((batches.data ?? []).map((b) => (b.id === updated.id ? updated : b)));
  }

  return (
    <>
      <PageHeader title="Dispatch">
        <button className="secondary" onClick={() => { batches.reload(); issues.reload(); }}>Refresh</button>
        <button onClick={createBatches} disabled={generate.busy}>Create batches from orders</button>
      </PageHeader>
      <p className="muted">
        Batches group the day's paid orders by office and drop point. Running it again adds new orders to batches
        that haven't been picked up yet.
      </p>
      {generate.error && <div className="notice bad">{generate.error}</div>}

      {(issues.data?.length ?? 0) > 0 && (
        <div className="card">
          <h2>Delivery issues</h2>
          <table>
            <thead><tr><th>When</th><th>Office</th><th>Partner</th><th>Orders</th><th>Reason</th></tr></thead>
            <tbody>
              {issues.data!.map((i) => (
                <tr key={i.id}>
                  <td className="nowrap">{dateTime(i.createdAt)}</td>
                  <td>{i.officeName}</td>
                  <td>{i.partnerName}</td>
                  <td>{i.orderIds.length} {i.partial ? <Badge tone="warn">partial</Badge> : <Badge tone="bad">failed</Badge>}</td>
                  <td>{i.reason}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <Loader loading={batches.loading && !batches.data} error={batches.error} onRetry={batches.reload}>
        {(batches.data ?? []).length === 0 && <p className="muted">No batches for this day yet.</p>}
        {(batches.data ?? []).map((b) => (
          <BatchCard key={b.id} batch={b} partners={partners.data ?? []} onUpdated={replace} />
        ))}
      </Loader>
    </>
  );
}

function BatchCard({ batch, partners, onUpdated }: { batch: AdminBatch; partners: AdminUser[]; onUpdated: (b: AdminBatch) => void }) {
  const editable = batch.status === "unassigned" || batch.status === "assigned";
  const [partnerId, setPartnerId] = useState(batch.partnerId ?? "");
  const [coordinatorName, setCoordinatorName] = useState(batch.coordinatorName ?? "");
  const [coordinatorPhone, setCoordinatorPhone] = useState(batch.coordinatorPhone ?? "");
  const [coordinatorOtp, setCoordinatorOtp] = useState(batch.coordinatorOtp != null);
  const [open, setOpen] = useState(batch.status === "unassigned");
  const assign = useAction();

  async function save() {
    const updated = await assign.run(() =>
      api.put<AdminBatch>(`/admin/batches/${batch.id}/assign`, {
        partnerId,
        coordinatorName: coordinatorName || null,
        coordinatorPhone: coordinatorPhone || null,
        coordinatorOtp,
      }),
    );
    if (updated) {
      onUpdated(updated);
      setOpen(false);
    }
  }

  return (
    <div className="card">
      <div className="row">
        <h3 className="grow" style={{ margin: 0 }}>
          {batch.officeName}
          {batch.dropPointName && <span className="muted"> · {batch.dropPointName}</span>}
        </h3>
        <Badge tone={batchTone[batch.status]}>{label(batch.status)}</Badge>
      </div>
      <p className="muted small">
        {plural(batch.orderCount, "order")} · {plural(batch.totalMeals, "meal")}
        {batch.partnerName && <> · Partner: <strong>{batch.partnerName}</strong></>}
        {batch.pickupCount != null && <> · Picked up {batch.pickupCount}</>}
      </p>
      {batch.coordinatorOtp && (
        <p>
          Coordinator handover code: <span className="otp">{batch.coordinatorOtp}</span>
          <span className="muted small"> — share it with {batch.coordinatorName ?? "the office coordinator"}</span>
        </p>
      )}

      {editable && open && (
        <div className="form-grid" style={{ marginBottom: 12 }}>
          <label>
            Delivery partner
            <select value={partnerId} onChange={(e) => setPartnerId(e.target.value)}>
              <option value="">Choose…</option>
              {partners.map((p) => (
                <option key={p.id} value={p.id}>{p.name} ({p.phone})</option>
              ))}
            </select>
          </label>
          <label>
            Office coordinator
            <input value={coordinatorName} onChange={(e) => setCoordinatorName(e.target.value)} placeholder="Optional" />
          </label>
          <label>
            Coordinator phone
            <input value={coordinatorPhone} onChange={(e) => setCoordinatorPhone(e.target.value)} placeholder="Optional" />
          </label>
          <label className="inline">
            <input type="checkbox" checked={coordinatorOtp} onChange={(e) => setCoordinatorOtp(e.target.checked)} />
            Coordinator collects the whole batch with one code
          </label>
          <div className="row">
            <button onClick={save} disabled={!partnerId || assign.busy}>{batch.partnerId ? "Reassign" : "Assign"}</button>
            {batch.partnerId && <button className="secondary" onClick={() => setOpen(false)}>Cancel</button>}
          </div>
        </div>
      )}
      {assign.error && <div className="notice bad">{assign.error}</div>}
      {editable && !open && <button className="secondary small" onClick={() => setOpen(true)}>Change assignment</button>}

      <details style={{ marginTop: 8 }}>
        <summary className="muted small">Orders in this batch</summary>
        <table>
          <tbody>
            {batch.orders.map((o) => (
              <tr key={o.orderId}>
                <td>{o.customerName}</td>
                <td>{o.lines.map((l) => `${l.quantity} × ${l.name}`).join(", ")}</td>
                <td><StatusBadge status={o.status} /></td>
              </tr>
            ))}
          </tbody>
        </table>
      </details>
    </div>
  );
}
