import { useEffect, useRef, useState } from "react";
import type { OrderStatus } from "../api/types";
import { statusLabels, statusTone } from "../format";
import { Icon, type IconName } from "./icons";

export function StatusBadge({ status }: { status: OrderStatus }) {
  return <span className={`badge ${statusTone[status]}`}>{statusLabels[status]}</span>;
}

export function Badge({ tone = "muted", children }: { tone?: "ok" | "warn" | "bad" | "muted"; children: React.ReactNode }) {
  return <span className={`badge ${tone}`}>{children}</span>;
}

/** Counts up to a number when it first appears or changes; anything else renders as-is. */
function CountUp({ value }: { value: React.ReactNode }) {
  const target = typeof value === "number" ? value : null;
  const [shown, setShown] = useState(target ?? 0);
  const from = useRef(0);
  useEffect(() => {
    if (target == null) return;
    const start = performance.now();
    const begin = from.current;
    let frame = 0;
    const tick = (t: number) => {
      const p = Math.min(1, (t - start) / 600);
      const eased = 1 - Math.pow(1 - p, 3);
      setShown(Math.round(begin + (target - begin) * eased));
      if (p < 1) frame = requestAnimationFrame(tick);
      else from.current = target;
    };
    frame = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(frame);
  }, [target]);
  return <>{target == null ? value : shown.toLocaleString("en-IN")}</>;
}

export function Stat({
  label,
  value,
  tone,
  icon,
  hero,
}: {
  label: string;
  value: React.ReactNode;
  tone?: "warn" | "bad" | "ok";
  icon?: IconName;
  hero?: boolean;
}) {
  return (
    <div className={`stat ${tone ?? ""} ${hero ? "hero" : ""}`}>
      {icon && (
        <div className="stat-icon">
          <Icon name={icon} />
        </div>
      )}
      <div className="stat-value">
        <CountUp value={value} />
      </div>
      <div className="muted small">{label}</div>
    </div>
  );
}

/** Loading / error / empty states around a page section. */
export function Loader({ loading, error, onRetry, children }: { loading: boolean; error: string | null; onRetry?: () => void; children: React.ReactNode }) {
  if (error)
    return (
      <div className="notice bad">
        <span className="grow">{error}</span>
        {onRetry && (
          <button className="secondary small" onClick={onRetry}>
            <Icon name="refresh" size={16} /> Retry
          </button>
        )}
      </div>
    );
  if (loading)
    return (
      <div aria-busy="true">
        <div className="skeleton-grid">
          {[0, 1, 2, 3].map((i) => (
            <div key={i} className="skeleton" style={{ height: 112 }} />
          ))}
        </div>
        <div className="skeleton" style={{ height: 240 }} />
      </div>
    );
  return <>{children}</>;
}

export function PageHeader({ title, subtitle, children }: { title: string; subtitle?: string; children?: React.ReactNode }) {
  return (
    <div className="page-header">
      <div className="grow">
        <h1>{title}</h1>
        {subtitle && <div className="muted">{subtitle}</div>}
      </div>
      <div className="actions">{children}</div>
    </div>
  );
}
