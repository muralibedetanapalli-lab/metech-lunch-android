import { useState } from "react";
import { api } from "../api/client";
import { useAction, useLoad } from "../api/hooks";
import type { AdminUser, UserRole } from "../api/types";
import { Badge, Loader, PageHeader } from "../components/ui";
import { useApp } from "../context";
import { dateTime } from "../format";

const TABS: [UserRole, string][] = [
  ["delivery_partner", "Delivery partners"],
  ["kitchen_staff", "Kitchen staff"],
  ["owner", "Owners"],
  ["customer", "Customers"],
];

export default function PeoplePage() {
  const { me } = useApp();
  const [role, setRole] = useState<UserRole>("delivery_partner");
  const users = useLoad(() => api.get<AdminUser[]>(`/admin/users?role=${role}`), [role]);
  const [name, setName] = useState("");
  const [phone, setPhone] = useState("");
  const create = useAction();
  const update = useAction();

  async function add(e: React.FormEvent) {
    e.preventDefault();
    const user = await create.run(() => api.post<AdminUser>("/admin/users", { name, phone, role }));
    if (user) {
      users.setData([...(users.data ?? []), user].sort((a, b) => a.name.localeCompare(b.name)));
      setName("");
      setPhone("");
    }
  }

  async function toggle(u: AdminUser) {
    if (u.active && !confirm(`Deactivate ${u.name}? They can no longer sign in, and an open session ends within the hour.`)) return;
    const updated = await update.run(() => api.put<AdminUser>(`/admin/users/${u.id}`, { name: u.name, active: !u.active }));
    if (updated) users.setData((users.data ?? []).map((x) => (x.id === updated.id ? updated : x)));
  }

  return (
    <>
      <PageHeader title="People" />
      <div className="segmented" style={{ maxWidth: 640, marginBottom: 16 }}>
        {TABS.map(([r, text]) => (
          <button key={r} className={role === r ? "active" : ""} onClick={() => setRole(r)}>{text}</button>
        ))}
      </div>

      {role !== "customer" ? (
        <form className="card form-grid" onSubmit={add} style={{ marginBottom: 16 }}>
          <label>Name<input value={name} onChange={(e) => setName(e.target.value)} required /></label>
          <label>Phone<input type="tel" value={phone} onChange={(e) => setPhone(e.target.value)} required /></label>
          <button type="submit" disabled={create.busy}>Add {TABS.find(([r]) => r === role)![1].toLowerCase().replace(/s$/, "")}</button>
          {create.error && <p className="error">{create.error}</p>}
        </form>
      ) : (
        <p className="muted">Customers register themselves in the app with their phone number.</p>
      )}
      {update.error && <div className="notice bad">{update.error}</div>}

      <Loader loading={users.loading && !users.data} error={users.error} onRetry={users.reload}>
        <div className="table-wrap">
          <table>
            <thead>
              <tr><th>Name</th><th>Phone</th>{role === "customer" && <th className="right">Orders</th>}<th>Joined</th><th>Status</th><th></th></tr>
            </thead>
            <tbody>
              {(users.data ?? []).map((u) => (
                <tr key={u.id}>
                  <td>{u.name || <span className="muted">(no name yet)</span>}{u.email && <div className="muted small">{u.email}</div>}</td>
                  <td>{u.phone}</td>
                  {role === "customer" && <td className="right">{u.orderCount ?? 0}</td>}
                  <td className="nowrap">{dateTime(u.createdAt)}</td>
                  <td>{u.active ? <Badge tone="ok">Active</Badge> : <Badge>Inactive</Badge>}</td>
                  <td>
                    {u.id !== me.id && (
                      <button className="secondary small" disabled={update.busy} onClick={() => toggle(u)}>
                        {u.active ? "Deactivate" : "Reactivate"}
                      </button>
                    )}
                  </td>
                </tr>
              ))}
              {(users.data ?? []).length === 0 && <tr><td colSpan={6} className="muted">Nobody here yet.</td></tr>}
            </tbody>
          </table>
        </div>
      </Loader>
    </>
  );
}
