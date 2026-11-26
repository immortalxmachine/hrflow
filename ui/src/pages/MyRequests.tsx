import { useEffect, useState } from "react";
import { api, ApiError } from "../api";
import type { RequestDto } from "../types";
import { StatusBadge } from "./widgets";

/** The current user's requests + a form to raise a leave request. */
export function MyRequests() {
  const [rows, setRows] = useState<RequestDto[]>([]);
  const [leaveType, setLeaveType] = useState("ANNUAL");
  const [startDate, setStartDate] = useState("");
  const [endDate, setEndDate] = useState("");
  const [msg, setMsg] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function refresh() {
    try {
      setRows((await api.myRequests()).content);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to load");
    }
  }
  useEffect(() => { void refresh(); }, []);

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setMsg(null);
    setError(null);
    try {
      await api.createLeave(leaveType, startDate, endDate);
      setMsg("Leave request submitted.");
      setStartDate("");
      setEndDate("");
      await refresh();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to submit");
    }
  }

  return (
    <div className="stack">
      <section className="card">
        <h2>Raise a leave request</h2>
        <form className="row-form" onSubmit={submit}>
          <label>Type
            <select value={leaveType} onChange={(e) => setLeaveType(e.target.value)}>
              <option>ANNUAL</option><option>SICK</option><option>CASUAL</option>
            </select>
          </label>
          <label>Start
            <input type="date" value={startDate} onChange={(e) => setStartDate(e.target.value)} required />
          </label>
          <label>End
            <input type="date" value={endDate} onChange={(e) => setEndDate(e.target.value)} required />
          </label>
          <button className="primary" type="submit">Submit</button>
        </form>
        {msg && <div className="ok">{msg}</div>}
        {error && <div className="error">{error}</div>}
      </section>

      <section className="card">
        <h2>My requests</h2>
        {rows.length === 0 ? <p className="muted">No requests yet.</p> : (
          <table className="grid">
            <thead><tr><th>#</th><th>Type</th><th>Status</th><th>Step</th><th>Details</th><th>Raised</th></tr></thead>
            <tbody>
              {rows.map((r) => (
                <tr key={r.id}>
                  <td>{r.id}</td>
                  <td>{r.requestType}</td>
                  <td><StatusBadge status={r.status} /></td>
                  <td>{r.status === "PENDING" ? `${r.currentStepOrder} → ${r.currentApproverRole}${r.escalated ? " ⚠" : ""}` : "—"}</td>
                  <td className="mono">{JSON.stringify(r.payload)}</td>
                  <td>{new Date(r.createdAt).toLocaleDateString()}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>
    </div>
  );
}
