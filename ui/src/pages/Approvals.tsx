import { useEffect, useState } from "react";
import { api, ApiError } from "../api";
import type { RequestDto } from "../types";

/** Approvals inbox: requests the current user can act on now. */
export function Approvals({ isAdmin }: { isAdmin: boolean }) {
  const [rows, setRows] = useState<RequestDto[]>([]);
  const [comment, setComment] = useState<Record<number, string>>({});
  const [msg, setMsg] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function refresh() {
    try {
      setRows(await api.inbox());
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to load inbox");
    }
  }
  useEffect(() => { void refresh(); }, []);

  async function decide(id: number, decision: "APPROVED" | "REJECTED") {
    setMsg(null);
    setError(null);
    try {
      await api.decide(id, decision, comment[id] ?? "");
      setMsg(`Request ${id} ${decision.toLowerCase()}.`);
      await refresh();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Decision failed");
    }
  }

  async function runEscalation() {
    setMsg(null); setError(null);
    try {
      const r = await api.runEscalation();
      setMsg(`Escalation sweep: ${r.escalated} request(s) escalated.`);
      await refresh();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Escalation failed");
    }
  }

  return (
    <div className="stack">
      <section className="card">
        <div className="card-head">
          <h2>Approvals inbox</h2>
          {isAdmin && <button className="secondary" onClick={runEscalation}>Run SLA escalation sweep</button>}
        </div>
        {msg && <div className="ok">{msg}</div>}
        {error && <div className="error">{error}</div>}
        {rows.length === 0 ? <p className="muted">Nothing awaiting your approval.</p> : (
          <table className="grid">
            <thead><tr><th>#</th><th>Type</th><th>Step</th><th>Details</th><th>Comment</th><th></th></tr></thead>
            <tbody>
              {rows.map((r) => (
                <tr key={r.id}>
                  <td>{r.id}</td>
                  <td>{r.requestType}</td>
                  <td>{r.currentStepOrder} → {r.currentApproverRole}{r.escalated && <span className="warn"> ⚠ escalated</span>}</td>
                  <td className="mono">{JSON.stringify(r.payload)}</td>
                  <td>
                    <input placeholder="optional" value={comment[r.id] ?? ""}
                      onChange={(e) => setComment({ ...comment, [r.id]: e.target.value })} />
                  </td>
                  <td className="actions">
                    <button className="approve" onClick={() => decide(r.id, "APPROVED")}>Approve</button>
                    <button className="reject" onClick={() => decide(r.id, "REJECTED")}>Reject</button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>
    </div>
  );
}
