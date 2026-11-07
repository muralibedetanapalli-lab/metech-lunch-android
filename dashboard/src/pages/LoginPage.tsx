import { useEffect, useRef, useState } from "react";
import { api, session } from "../api/client";
import { useAction } from "../api/hooks";
import type { AuthTokens, UserRole } from "../api/types";
import { Icon, Logo } from "../components/icons";

const DIGITS = 6;

export default function LoginPage() {
  const [role, setRole] = useState<UserRole>("owner");
  const [phone, setPhone] = useState("");
  const [code, setCode] = useState<string[]>(Array(DIGITS).fill(""));
  const [otpSent, setOtpSent] = useState(false);
  const [shake, setShake] = useState(false);
  const { busy, error, run } = useAction();
  const boxes = useRef<(HTMLInputElement | null)[]>([]);
  const fullPhone = "+91" + phone;
  const otp = code.join("");

  useEffect(() => {
    if (otpSent) boxes.current[0]?.focus();
  }, [otpSent]);
  useEffect(() => {
    if (!error) return;
    setShake(true);
    const t = setTimeout(() => setShake(false), 400);
    return () => clearTimeout(t);
  }, [error]);

  async function requestCode() {
    const ok = await run(async () => {
      await api.post("/auth/otp/request", { phone: fullPhone, role });
      return true;
    });
    if (ok) {
      setCode(Array(DIGITS).fill(""));
      setOtpSent(true);
    }
  }

  async function verify(value = otp) {
    const tokens = await run(() => api.post<AuthTokens>("/auth/otp/verify", { phone: fullPhone, otp: value, role }));
    if (tokens) session.save(tokens);
  }

  function submit(e: React.FormEvent) {
    e.preventDefault();
    if (!otpSent) requestCode();
    else verify();
  }

  function setDigit(i: number, raw: string) {
    const digits = raw.replace(/\D/g, "");
    if (!digits) {
      setCode((c) => c.map((d, j) => (j === i ? "" : d)));
      return;
    }
    // Typing or pasting several digits fills the following boxes.
    const next = [...code];
    let j = i;
    for (const d of digits) {
      if (j >= DIGITS) break;
      next[j++] = d;
    }
    setCode(next);
    boxes.current[Math.min(j, DIGITS - 1)]?.focus();
    if (next.every(Boolean) && !busy) verify(next.join(""));
  }

  return (
    <div className="login">
      <section className="login-hero">
        <span className="float" style={{ top: "12%", right: "14%" }}>🍛</span>
        <span className="float" style={{ bottom: "16%", right: "28%", animationDelay: "1.2s" }}>🥗</span>
        <span className="float" style={{ top: "58%", right: "8%", animationDelay: "2.1s" }}>🍚</span>
        <Logo size={72} />
        <h1>Metech Lunch<br />Kitchen HQ</h1>
        <p>Menus, orders, prep and deliveries for today's lunch, all in one place.</p>
        <div className="features">
          <div><span>🧑‍🍳</span> Know exactly how many meals to cook</div>
          <div><span>🛵</span> Batch deliveries and track handovers</div>
          <div><span>📊</span> Sales, refunds and reports at a glance</div>
        </div>
      </section>

      <section className="login-form">
        <form onSubmit={submit}>
          <div>
            <h2>{otpSent ? "Enter your code" : "Sign in"}</h2>
            <p className="muted" style={{ margin: "6px 0 0" }}>
              {otpSent ? `We sent a 6-digit code to +91 ${phone.slice(0, 5)} ${phone.slice(5)}.` : "Use the phone number registered for your role."}
            </p>
          </div>

          {!otpSent && (
            <>
              <div className="segmented" role="tablist">
                {(["owner", "kitchen_staff"] as const).map((r) => (
                  <button type="button" key={r} className={role === r ? "active" : ""} onClick={() => setRole(r)}>
                    {r === "owner" ? "👑 Owner" : "🧑‍🍳 Kitchen staff"}
                  </button>
                ))}
              </div>
              <label>
                Phone number
                <div className="phone-field">
                  <span className="prefix">🇮🇳 +91</span>
                  <input
                    type="tel"
                    inputMode="numeric"
                    placeholder="98765 43210"
                    value={phone}
                    onChange={(e) => setPhone(e.target.value.replace(/\D/g, "").slice(-10))}
                    autoFocus
                    required
                  />
                </div>
              </label>
            </>
          )}

          {otpSent && (
            <div className={`otp-boxes ${shake ? "shake" : ""}`}>
              {code.map((d, i) => (
                <input
                  key={i}
                  ref={(el) => { boxes.current[i] = el; }}
                  className={d ? "filled" : ""}
                  inputMode="numeric"
                  autoComplete={i === 0 ? "one-time-code" : "off"}
                  aria-label={`Digit ${i + 1}`}
                  value={d}
                  onChange={(e) => setDigit(i, e.target.value)}
                  onKeyDown={(e) => {
                    if (e.key === "Backspace" && !d && i > 0) boxes.current[i - 1]?.focus();
                  }}
                  onFocus={(e) => e.target.select()}
                />
              ))}
            </div>
          )}

          {error && <div className="notice bad" style={{ margin: 0 }}>{error}</div>}

          <button type="submit" disabled={busy || (!otpSent ? phone.length !== 10 : otp.length !== DIGITS)}>
            {busy ? "Please wait…" : otpSent ? "Sign in" : "Send code"}
            {!busy && <Icon name="arrow" size={18} />}
          </button>
          {otpSent && (
            <div className="row" style={{ justifyContent: "space-between" }}>
              <button type="button" className="link" onClick={() => setOtpSent(false)}>Use a different number</button>
              <button type="button" className="link" onClick={requestCode} disabled={busy}>Resend code</button>
            </div>
          )}
          <p className="muted small" style={{ display: "flex", gap: 6, alignItems: "center", margin: 0 }}>
            <Icon name="lock" size={14} /> Only owners and kitchen staff can sign in here.
          </p>
        </form>
      </section>
    </div>
  );
}
