import { useEffect, useState } from "react";
import { NavLink, Navigate, Route, Routes } from "react-router-dom";
import { api, session } from "./api/client";
import { useLoad } from "./api/hooks";
import type { KitchenSettings, UserProfile } from "./api/types";
import { Ctx, useApp } from "./context";
import { prettyDate } from "./format";
import LoginPage from "./pages/LoginPage";
import OverviewPage from "./pages/OverviewPage";
import OrdersPage from "./pages/OrdersPage";
import KitchenPage from "./pages/KitchenPage";
import DispatchPage from "./pages/DispatchPage";
import MenuPage from "./pages/MenuPage";
import RefundsPage from "./pages/RefundsPage";
import ReportsPage from "./pages/ReportsPage";
import PeoplePage from "./pages/PeoplePage";
import SettingsPage from "./pages/SettingsPage";

export default function App() {
  const [signedIn, setSignedIn] = useState(session.isSignedIn());
  useEffect(() => session.subscribe(setSignedIn), []);
  if (!signedIn) return <LoginPage />;
  return <SignedIn />;
}

function SignedIn() {
  const me = useLoad(() => api.get<UserProfile>("/me"), []);
  const kitchen = useLoad(() => api.get<KitchenSettings>("/admin/kitchen"), []);
  const [date, setDate] = useState<string | null>(null);

  useEffect(() => {
    if (kitchen.data && date == null) setDate(kitchen.data.today);
  }, [kitchen.data, date]);

  const error = me.error ?? kitchen.error;
  if (error) {
    return (
      <div className="centered">
        <div className="card narrow">
          <h2>Cannot open the dashboard</h2>
          <p className="error">{error}</p>
          <button onClick={() => session.clear()}>Sign out</button>
        </div>
      </div>
    );
  }
  if (!me.data || !kitchen.data || !date) return <div className="centered muted">Loading…</div>;

  const isOwner = me.data.role === "owner";
  return (
    <Ctx.Provider value={{ me: me.data, kitchen: kitchen.data, isOwner, date, setDate, reloadKitchen: kitchen.reload }}>
      <Layout>
        <Routes>
          <Route path="/" element={<OverviewPage />} />
          <Route path="/kitchen" element={<KitchenPage />} />
          <Route path="/dispatch" element={<DispatchPage />} />
          <Route path="/menu" element={<MenuPage />} />
          {isOwner && (
            <>
              <Route path="/orders" element={<OrdersPage />} />
              <Route path="/refunds" element={<RefundsPage />} />
              <Route path="/reports" element={<ReportsPage />} />
              <Route path="/people" element={<PeoplePage />} />
              <Route path="/settings" element={<SettingsPage />} />
            </>
          )}
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </Layout>
    </Ctx.Provider>
  );
}

function Layout({ children }: { children: React.ReactNode }) {
  const { me, kitchen, isOwner, date, setDate } = useApp();
  const [menuOpen, setMenuOpen] = useState(false);
  const links: [string, string, boolean][] = [
    ["/", "Overview", true],
    ["/orders", "Orders", isOwner],
    ["/kitchen", "Kitchen prep", true],
    ["/dispatch", "Dispatch", true],
    ["/menu", "Menu", true],
    ["/refunds", "Refunds", isOwner],
    ["/reports", "Reports", isOwner],
    ["/people", "People", isOwner],
    ["/settings", "Settings", isOwner],
  ];
  return (
    <div className="shell">
      <aside className={menuOpen ? "sidebar open" : "sidebar"}>
        <div className="brand">
          <strong>Metech Lunch</strong>
          <span className="muted small">{kitchen.name}</span>
        </div>
        <nav onClick={() => setMenuOpen(false)}>
          {links
            .filter(([, , show]) => show)
            .map(([to, text]) => (
              <NavLink key={to} to={to} end={to === "/"}>
                {text}
              </NavLink>
            ))}
        </nav>
        <div className="who">
          <div>{me.name || me.phone}</div>
          <div className="muted small">{isOwner ? "Owner" : "Kitchen staff"}</div>
          <button className="link" onClick={() => session.clear()}>
            Sign out
          </button>
        </div>
      </aside>
      <main>
        <header className="topbar no-print">
          <button className="menu-toggle" onClick={() => setMenuOpen(!menuOpen)} aria-label="Menu">
            ☰
          </button>
          <label className="date-pick">
            <span className="muted small">Day</span>
            <input type="date" value={date} onChange={(e) => e.target.value && setDate(e.target.value)} />
          </label>
          {date !== kitchen.today && (
            <button className="secondary" onClick={() => setDate(kitchen.today)}>
              Today
            </button>
          )}
          <span className="muted small grow">{prettyDate(date)} · cutoff {kitchen.cutoffTime.slice(0, 5)}</span>
        </header>
        <div className="content">{children}</div>
      </main>
    </div>
  );
}
