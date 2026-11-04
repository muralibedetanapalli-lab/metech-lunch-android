import { useState } from "react";
import { api } from "../api/client";
import { useAction, useLoad } from "../api/hooks";
import type { AdminOffice, AuditEntry, KitchenSettings } from "../api/types";
import { Badge, Loader, PageHeader } from "../components/ui";
import { useApp } from "../context";
import { dateTime, label } from "../format";

export default function SettingsPage() {
  return (
    <>
      <PageHeader title="Settings" />
      <KitchenForm />
      <Offices />
      <Audit />
    </>
  );
}

function KitchenForm() {
  const { kitchen, reloadKitchen } = useApp();
  const [name, setName] = useState(kitchen.name);
  const [address, setAddress] = useState(kitchen.address);
  const [cutoff, setCutoff] = useState(kitchen.cutoffTime.slice(0, 5));
  const [saved, setSaved] = useState(false);
  const save = useAction();

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    const ok = await save.run(() => api.put<KitchenSettings>("/admin/kitchen", { name, address, cutoffTime: cutoff }));
    if (ok) {
      setSaved(true);
      reloadKitchen();
    }
  }

  return (
    <form className="card" onSubmit={submit}>
      <h2>Kitchen</h2>
      <div className="form-grid">
        <label>Name<input value={name} onChange={(e) => { setName(e.target.value); setSaved(false); }} required /></label>
        <label>Pickup address<input value={address} onChange={(e) => { setAddress(e.target.value); setSaved(false); }} required /></label>
        <label>Daily ordering cutoff<input type="time" value={cutoff} onChange={(e) => { setCutoff(e.target.value); setSaved(false); }} required /></label>
        <button type="submit" disabled={save.busy}>Save</button>
      </div>
      <p className="muted small" style={{ marginTop: 8 }}>Times are in {kitchen.timezone}. Changing the cutoff applies immediately, including today.</p>
      {save.error && <p className="error">{save.error}</p>}
      {saved && <p className="muted">Saved.</p>}
    </form>
  );
}

function Offices() {
  const offices = useLoad(() => api.get<AdminOffice[]>("/admin/offices"), []);
  const act = useAction();
  const [newOffice, setNewOffice] = useState({ name: "", address: "" });

  async function run(action: () => Promise<AdminOffice[]>) {
    const result = await act.run(action);
    if (result) offices.setData(result);
    return result;
  }

  return (
    <div className="card">
      <h2>Offices and drop points</h2>
      <p className="muted small">Customers can only order to active offices. Deactivating an office stops new orders but keeps its history.</p>
      {act.error && <div className="notice bad">{act.error}</div>}
      <Loader loading={offices.loading && !offices.data} error={offices.error} onRetry={offices.reload}>
        {(offices.data ?? []).map((o) => (
          <div key={o.id} style={{ borderTop: "1px solid var(--border)", padding: "12px 0" }}>
            <div className="row">
              <strong className="grow">{o.name} <span className="muted small">{o.address}</span></strong>
              {o.active ? <Badge tone="ok">Active</Badge> : <Badge>Inactive</Badge>}
              <button className="secondary small" disabled={act.busy}
                onClick={() => run(() => api.put(`/admin/offices/${o.id}`, { name: o.name, address: o.address, active: !o.active }))}>
                {o.active ? "Deactivate" : "Activate"}
              </button>
            </div>
            <div className="row" style={{ marginTop: 8 }}>
              {o.dropPoints.map((d) => (
                <span key={d.id} className="row" style={{ gap: 4 }}>
                  <Badge tone={d.active ? "ok" : "muted"}>{d.name}</Badge>
                  <button className="link small" disabled={act.busy}
                    onClick={() => run(() => api.put(`/admin/drop-points/${d.id}`, { name: d.name, instructions: d.instructions ?? null, active: !d.active }))}>
                    {d.active ? "disable" : "enable"}
                  </button>
                </span>
              ))}
              <AddDropPoint onAdd={(name) => run(() => api.post(`/admin/offices/${o.id}/drop-points`, { name }))} />
            </div>
          </div>
        ))}
      </Loader>
      <form
        className="form-grid"
        style={{ borderTop: "1px solid var(--border)", paddingTop: 12 }}
        onSubmit={async (e) => {
          e.preventDefault();
          if (await run(() => api.post("/admin/offices", newOffice))) setNewOffice({ name: "", address: "" });
        }}
      >
        <label>New office<input value={newOffice.name} onChange={(e) => setNewOffice({ ...newOffice, name: e.target.value })} required /></label>
        <label>Address<input value={newOffice.address} onChange={(e) => setNewOffice({ ...newOffice, address: e.target.value })} required /></label>
        <button type="submit" disabled={act.busy}>Add office</button>
      </form>
    </div>
  );
}

function AddDropPoint({ onAdd }: { onAdd: (name: string) => Promise<unknown> }) {
  const [name, setName] = useState("");
  return (
    <form
      className="row"
      onSubmit={async (e) => {
        e.preventDefault();
        if (name.trim() && (await onAdd(name.trim()))) setName("");
      }}
    >
      <input placeholder="New drop point" value={name} onChange={(e) => setName(e.target.value)} style={{ width: 160 }} />
      <button className="secondary small" type="submit">Add</button>
    </form>
  );
}

function Audit() {
  const audit = useLoad(() => api.get<AuditEntry[]>("/admin/audit?limit=100"), []);
  return (
    <div className="card">
      <div className="row">
        <h2 className="grow">Activity log</h2>
        <button className="secondary small" onClick={audit.reload}>Refresh</button>
      </div>
      <Loader loading={audit.loading && !audit.data} error={audit.error} onRetry={audit.reload}>
        <div className="table-wrap" style={{ maxHeight: 400 }}>
          <table>
            <thead><tr><th>When</th><th>Who</th><th>What</th><th>Details</th></tr></thead>
            <tbody>
              {(audit.data ?? []).map((a) => (
                <tr key={a.id}>
                  <td className="nowrap">{dateTime(a.createdAt)}</td>
                  <td>{a.actorName ?? <span className="muted">system</span>}</td>
                  <td>{label(a.action)} <span className="muted small">{a.entity} {a.entityId.slice(0, 8)}</span></td>
                  <td className="small">{a.details}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Loader>
    </div>
  );
}
