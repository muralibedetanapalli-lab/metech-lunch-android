import { useEffect, useState } from "react";
import { NavLink, Navigate, Route, Routes } from "react-router-dom";
import { api, session } from "./api/client";
import { useLoad } from "./api/hooks";
import type { KitchenSettings, UserProfile } from "./api/types";
import { Ctx, useApp } from "./context";
import { formatLeft, prettyDate, useCutoffCountdown } from "./format";
import { Icon, Logo, type IconName } from "./components/icons";
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
  if (!me.data || !kitchen.data || !date) {
    return (
      <div className="centered">
        <div className="pulse"><Logo size={64} /></div>
      </div>
    );
  }

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
  const daily: [string, string, IconName][] = [
    ["/", "Overview", "home"],
    ["/kitchen", "Kitchen prep", "chef"],
    ["/dispatch", "Dispatch", "truck"],
    ["/menu", "Menu", "book"],
  ];
  const business: [string, string, IconName][] = [
    ["/orders", "Orders", "receipt"],
    ["/refunds", "Refunds", "refund"],
    ["/reports", "Reports", "chart"],
    ["/people", "People", "users"],
    ["/settings", "Settings", "settings"],
  ];
  const name = me.name || me.phone;
  return (
    <div className="shell">
      {menuOpen && <div className="scrim" onClick={() => setMenuOpen(false)} />}
      <aside className={menuOpen ? "sidebar open" : "sidebar"}>
        <div className="brand">
          <Logo size={42} />
          <div>
            <strong>Metech Lunch</strong>
            <span>{kitchen.name}</span>
          </div>
        </div>
        <nav onClick={() => setMenuOpen(false)}>
          <div className="nav-label">Daily work</div>
          {daily.map(([to, text, icon]) => (
            <NavLink key={to} to={to} end={to === "/"}>
              <Icon name={icon} />
              {text}
            </NavLink>
          ))}
          {isOwner && (
            <>
              <div className="nav-label">Business</div>
              {business.map(([to, text, icon]) => (
                <NavLink key={to} to={to}>
                  <Icon name={icon} />
                  {text}
                </NavLink>
              ))}
            </>
          )}
        </nav>
        <div className="who">
          <div className="avatar">{name.trim().charAt(0).toUpperCase()}</div>
          <div className="who-text">
            <div>{name}</div>
            <div>{isOwner ? "Owner" : "Kitchen staff"}</div>
          </div>
          <button title="Sign out" aria-label="Sign out" onClick={() => session.clear()}>
            <Icon name="logout" size={18} />
          </button>
        </div>
      </aside>
      <main>
        <header className="topbar no-print">
          <button className="menu-toggle" onClick={() => setMenuOpen(!menuOpen)} aria-label="Menu">
            <Icon name="menu" size={22} />
          </button>
          <label className="date-pick">
            <Icon name="calendar" size={18} />
            <input type="date" value={date} onChange={(e) => e.target.value && setDate(e.target.value)} />
          </label>
          {date !== kitchen.today && (
            <button className="secondary small" onClick={() => setDate(kitchen.today)}>
              Back to today
            </button>
          )}
          <span className="muted small grow">{prettyDate(date)}</span>
          {date === kitchen.today && <CutoffChip cutoff={kitchen.cutoffTime} timezone={kitchen.timezone} />}
        </header>
        <div className="content" key={date}>{children}</div>
      </main>
    </div>
  );
}

/** "Open · 1h 12m left" until the cutoff, then "Closed at 10:00", using the kitchen's timezone. */
function CutoffChip({ cutoff, timezone }: { cutoff: string; timezone: string }) {
  const left = useCutoffCountdown(cutoff, timezone);
  // Whether ordering is open is the server's call (see Overview); this chip only counts down.
  if (left == null || left <= 0) {
    return <span className="chip closed"><Icon name="clock" size={16} />Cutoff {cutoff.slice(0, 5)}</span>;
  }
  const urgent = left < 30 * 60;
  return (
    <span className={`chip ${urgent ? "urgent" : "open"}`}>
      <span className={urgent ? "pulse" : ""}>●</span> Ordering open · {formatLeft(left)} left
    </span>
  );
}
