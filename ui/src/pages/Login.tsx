import { useState } from "react";
import { api, ApiError } from "../api";
import type { LoginResponse } from "../types";

/** Login screen. Exchanges employee code + password for a JWT. */
export function Login({ onLogin }: { onLogin: (resp: LoginResponse) => void }) {
  const [employeeCode, setEmployeeCode] = useState("SYSADMIN");
  const [password, setPassword] = useState("Password123!");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setBusy(true);
    try {
      onLogin(await api.login(employeeCode, password));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Login failed");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="login-wrap">
      <form className="card login" onSubmit={submit}>
        <h1>HRFlow</h1>
        <p className="muted">HRMS workflow &amp; access-control console</p>
        <label>Employee code
          <input value={employeeCode} onChange={(e) => setEmployeeCode(e.target.value)} autoFocus />
        </label>
        <label>Password
          <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} />
        </label>
        {error && <div className="error">{error}</div>}
        <button className="primary" disabled={busy}>{busy ? "Signing in…" : "Sign in"}</button>
        <p className="hint">
          Employees seeded from the public IBM HR Attrition dataset (no real people).
          Seeded accounts use password <code>Password123!</code>. Try <code>SYSADMIN</code>,
          <code>AE-HR-0103</code> (HR), or <code>IN-MG-0023</code> (manager).
        </p>
      </form>
    </div>
  );
}
