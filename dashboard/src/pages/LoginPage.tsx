import { useState } from "react";
import { api, session } from "../api/client";
import { useAction } from "../api/hooks";
import type { AuthTokens, UserRole } from "../api/types";

export default function LoginPage() {
  const [role, setRole] = useState<UserRole>("owner");
  const [phone, setPhone] = useState("");
  const [otp, setOtp] = useState("");
  const [otpSent, setOtpSent] = useState(false);
  const { busy, error, run } = useAction();

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    if (!otpSent) {
      const ok = await run(async () => {
        await api.post("/auth/otp/request", { phone, role });
        return true;
      });
      if (ok) setOtpSent(true);
    } else {
      const tokens = await run(() => api.post<AuthTokens>("/auth/otp/verify", { phone, otp, role }));
      if (tokens) session.save(tokens);
    }
  }

  return (
    <div className="centered">
      <form className="card narrow" onSubmit={submit}>
        <h1>Metech Lunch</h1>
        <p className="muted">Owner and kitchen dashboard</p>

        <div className="segmented">
          {(["owner", "kitchen_staff"] as const).map((r) => (
            <button
              type="button"
              key={r}
              className={role === r ? "active" : ""}
              onClick={() => {
                setRole(r);
                setOtpSent(false);
              }}
            >
              {r === "owner" ? "Owner" : "Kitchen staff"}
            </button>
          ))}
        </div>

        <label>
          Phone number
          <input type="tel" value={phone} onChange={(e) => setPhone(e.target.value)} autoFocus required disabled={otpSent} />
        </label>
        {otpSent && (
          <label>
            OTP
            <input inputMode="numeric" value={otp} onChange={(e) => setOtp(e.target.value)} autoFocus required />
          </label>
        )}
        {error && <p className="error">{error}</p>}
        <button type="submit" disabled={busy}>
          {otpSent ? "Sign in" : "Send OTP"}
        </button>
        {otpSent && (
          <button type="button" className="link" onClick={() => setOtpSent(false)}>
            Use a different number
          </button>
        )}
      </form>
    </div>
  );
}
