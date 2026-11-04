import type { OrderStatus } from "../api/types";
import { statusLabels, statusTone } from "../format";

export function StatusBadge({ status }: { status: OrderStatus }) {
  return <span className={`badge ${statusTone[status]}`}>{statusLabels[status]}</span>;
}

export function Badge({ tone = "muted", children }: { tone?: "ok" | "warn" | "bad" | "muted"; children: React.ReactNode }) {
  return <span className={`badge ${tone}`}>{children}</span>;
}

export function Stat({ label, value, tone }: { label: string; value: React.ReactNode; tone?: "warn" | "bad" }) {
  return (
    <div className={`stat ${tone ?? ""}`}>
      <div className="stat-value">{value}</div>
      <div className="muted small">{label}</div>
    </div>
  );
}

/** Loading / error / empty states around a page section. */
export function Loader({ loading, error, onRetry, children }: { loading: boolean; error: string | null; onRetry?: () => void; children: React.ReactNode }) {
  if (error)
    return (
      <div className="notice bad">
        {error} {onRetry && <button className="link" onClick={onRetry}>Retry</button>}
      </div>
    );
  if (loading) return <div className="muted">Loading…</div>;
  return <>{children}</>;
}

export function PageHeader({ title, children }: { title: string; children?: React.ReactNode }) {
  return (
    <div className="page-header">
      <h1>{title}</h1>
      <div className="actions">{children}</div>
    </div>
  );
}
