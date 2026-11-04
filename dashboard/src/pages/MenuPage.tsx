import { useEffect, useState } from "react";
import { api } from "../api/client";
import { useAction, useLoad } from "../api/hooks";
import type { AdminMenuItem, DailyMenuLine, DailyMenuView, SaveMenuItemRequest } from "../api/types";
import { Badge, Loader, PageHeader } from "../components/ui";
import { useApp } from "../context";
import { prettyDate, rupees, toPaise } from "../format";

interface Row {
  menuItemId: string;
  name: string;
  onMenu: boolean;
  price: string;
  capacity: string;
  available: boolean;
  reserved: number;
}

export default function MenuPage() {
  const { date, kitchen, isOwner } = useApp();
  const items = useLoad(() => api.get<AdminMenuItem[]>("/admin/menu-items"), []);
  const daily = useLoad(() => api.get<DailyMenuView>(`/admin/daily-menu?date=${date}`), [date]);
  const [rows, setRows] = useState<Row[]>([]);
  const [saved, setSaved] = useState(false);
  const save = useAction();
  const past = date < kitchen.today;

  // Build one editable row per catalogue meal, merged with what is published for the day.
  useEffect(() => {
    if (!items.data || !daily.data) return;
    const published = new Map(daily.data.published.map((p) => [p.menuItemId, p]));
    setRows(
      items.data
        .filter((i) => i.active || published.has(i.id))
        .map((i) => {
          const p = published.get(i.id);
          return {
            menuItemId: i.id,
            name: i.name,
            onMenu: p != null,
            price: ((p?.pricePaise ?? i.pricePaise) / 100).toString(),
            capacity: p?.capacity != null ? String(p.capacity) : p ? "" : "50",
            available: p?.available ?? true,
            reserved: p?.reserved ?? 0,
          };
        }),
    );
    setSaved(false);
  }, [items.data, daily.data]);

  function update(id: string, patch: Partial<Row>) {
    setRows((rs) => rs.map((r) => (r.menuItemId === id ? { ...r, ...patch } : r)));
    setSaved(false);
  }

  async function publish() {
    const lines: DailyMenuLine[] = [];
    for (const r of rows.filter((r) => r.onMenu)) {
      const pricePaise = toPaise(r.price);
      if (pricePaise == null) return save.run(() => Promise.reject(new Error(`Check the price for ${r.name}`)));
      const capacity = r.capacity.trim() === "" ? null : Number(r.capacity);
      if (capacity != null && (!Number.isInteger(capacity) || capacity < 0)) {
        return save.run(() => Promise.reject(new Error(`Capacity for ${r.name} must be a whole number`)));
      }
      lines.push({ menuItemId: r.menuItemId, pricePaise, capacity, available: r.available });
    }
    const result = await save.run(() => api.put<DailyMenuView>(`/admin/daily-menu?date=${date}`, { lines }));
    if (result) {
      daily.setData(result);
      setSaved(true);
    }
  }

  return (
    <>
      <PageHeader title="Menu">
        <span className="muted">{prettyDate(date)}</span>
        <button onClick={publish} disabled={past || save.busy || rows.length === 0}>Publish menu for this day</button>
      </PageHeader>
      {past && <div className="notice warn">Past menus are read-only.</div>}
      {save.error && <div className="notice bad">{save.error}</div>}
      {saved && <div className="notice ok">Menu published. Customers see it right away.</div>}

      <Loader loading={(items.loading || daily.loading) && rows.length === 0} error={items.error ?? daily.error} onRetry={() => { items.reload(); daily.reload(); }}>
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>On menu</th><th>Meal</th><th>Price (₹)</th><th>Capacity</th><th>Ordered</th><th>Accepting orders</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((r) => (
                <tr key={r.menuItemId}>
                  <td>
                    <input type="checkbox" checked={r.onMenu} disabled={past || r.reserved > 0}
                      title={r.reserved > 0 ? "Meals with orders stay on the menu; stop accepting orders instead" : undefined}
                      onChange={(e) => update(r.menuItemId, { onMenu: e.target.checked })} />
                  </td>
                  <td>{r.name}</td>
                  <td style={{ maxWidth: 110 }}>
                    <input value={r.price} disabled={past || !r.onMenu || r.reserved > 0} inputMode="decimal"
                      onChange={(e) => update(r.menuItemId, { price: e.target.value })} />
                  </td>
                  <td style={{ maxWidth: 110 }}>
                    <input value={r.capacity} disabled={past || !r.onMenu} inputMode="numeric" placeholder="No limit"
                      onChange={(e) => update(r.menuItemId, { capacity: e.target.value })} />
                  </td>
                  <td>{r.reserved}</td>
                  <td>
                    <input type="checkbox" checked={r.available} disabled={past || !r.onMenu}
                      onChange={(e) => update(r.menuItemId, { available: e.target.checked })} />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <p className="muted small">"Ordered" includes unpaid orders that are still within their 15-minute payment window.</p>
      </Loader>

      <Catalogue items={items.data ?? []} editable={isOwner} onChanged={items.reload} />
    </>
  );
}

const emptyItem: SaveMenuItemRequest = { name: "", description: "", imageUrl: "", pricePaise: 0, dietaryTags: [], allergens: [], active: true };

function Catalogue({ items, editable, onChanged }: { items: AdminMenuItem[]; editable: boolean; onChanged: () => void }) {
  const [editing, setEditing] = useState<{ id: string | null; form: SaveMenuItemRequest; price: string; tags: string; allergens: string } | null>(null);
  const save = useAction();

  function edit(item: AdminMenuItem | null) {
    const base = item ?? { ...emptyItem, id: null };
    setEditing({
      id: item?.id ?? null,
      form: { ...emptyItem, ...base, imageUrl: base.imageUrl ?? "" },
      price: item ? String(item.pricePaise / 100) : "",
      tags: base.dietaryTags.join(", "),
      allergens: base.allergens.join(", "),
    });
  }

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    if (!editing) return;
    const pricePaise = toPaise(editing.price);
    if (pricePaise == null) return save.run(() => Promise.reject(new Error("Enter a valid price")));
    const split = (s: string) => s.split(",").map((t) => t.trim()).filter(Boolean);
    const body: SaveMenuItemRequest = {
      ...editing.form,
      imageUrl: editing.form.imageUrl || null,
      pricePaise,
      dietaryTags: split(editing.tags),
      allergens: split(editing.allergens),
    };
    const ok = await save.run(() => (editing.id ? api.put(`/admin/menu-items/${editing.id}`, body) : api.post("/admin/menu-items", body)));
    if (ok) {
      setEditing(null);
      onChanged();
    }
  }

  return (
    <div className="card" style={{ marginTop: 16 }}>
      <div className="row">
        <h2 className="grow">Meal catalogue</h2>
        {editable && !editing && <button className="secondary" onClick={() => edit(null)}>Add meal</button>}
      </div>
      {editing && (
        <form onSubmit={submit} className="form-grid" style={{ marginBottom: 16 }}>
          <label>Name<input value={editing.form.name} required onChange={(e) => setEditing({ ...editing, form: { ...editing.form, name: e.target.value } })} /></label>
          <label>Default price (₹)<input value={editing.price} required inputMode="decimal" onChange={(e) => setEditing({ ...editing, price: e.target.value })} /></label>
          <label>Description<input value={editing.form.description} onChange={(e) => setEditing({ ...editing, form: { ...editing.form, description: e.target.value } })} /></label>
          <label>Image URL<input value={editing.form.imageUrl ?? ""} onChange={(e) => setEditing({ ...editing, form: { ...editing.form, imageUrl: e.target.value } })} /></label>
          <label>Dietary tags<input value={editing.tags} placeholder="veg, jain" onChange={(e) => setEditing({ ...editing, tags: e.target.value })} /></label>
          <label>Allergens<input value={editing.allergens} placeholder="dairy, nuts" onChange={(e) => setEditing({ ...editing, allergens: e.target.value })} /></label>
          <label className="inline">
            <input type="checkbox" checked={editing.form.active} onChange={(e) => setEditing({ ...editing, form: { ...editing.form, active: e.target.checked } })} />
            Active
          </label>
          <div className="row">
            <button type="submit" disabled={save.busy}>Save meal</button>
            <button type="button" className="secondary" onClick={() => setEditing(null)}>Cancel</button>
          </div>
          {save.error && <p className="error">{save.error}</p>}
        </form>
      )}
      <p className="muted small">Dietary and allergen information is supplied and approved by the client.</p>
      <table>
        <thead><tr><th>Meal</th><th>Default price</th><th>Tags</th><th>Status</th><th></th></tr></thead>
        <tbody>
          {items.map((i) => (
            <tr key={i.id}>
              <td>{i.name}<div className="muted small">{i.description}</div></td>
              <td>{rupees(i.pricePaise)}</td>
              <td className="small">{[...i.dietaryTags, ...i.allergens.map((a) => `contains ${a}`)].join(", ")}</td>
              <td>{i.active ? <Badge tone="ok">Active</Badge> : <Badge>Inactive</Badge>}</td>
              <td>{editable && <button className="secondary small" onClick={() => edit(i)}>Edit</button>}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
