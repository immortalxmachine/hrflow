import { useEffect, useState } from "react";
import { api, ApiError } from "../api";
import type { Role, StepDto, WorkflowDto } from "../types";

const ROLES: Role[] = ["EMPLOYEE", "MANAGER", "HR_ADMIN", "SYSTEM_ADMIN"];

/** Workflow config editor: view each chain and edit the selected one's steps. */
export function Workflows() {
  const [workflows, setWorkflows] = useState<WorkflowDto[]>([]);
  const [editing, setEditing] = useState<WorkflowDto | null>(null);
  const [msg, setMsg] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function refresh() {
    try {
      setWorkflows(await api.workflows());
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to load");
    }
  }
  useEffect(() => { void refresh(); }, []);

  function edit(w: WorkflowDto) {
    // Deep copy so edits don't mutate the table until saved.
    setEditing(JSON.parse(JSON.stringify(w)));
    setMsg(null);
    setError(null);
  }

  function updateStep(i: number, patch: Partial<StepDto>) {
    if (!editing) return;
    const steps = editing.steps.map((s, idx) => (idx === i ? { ...s, ...patch } : s));
    setEditing({ ...editing, steps });
  }

  function addStep() {
    if (!editing) return;
    const next = editing.steps.length + 1;
    setEditing({ ...editing, steps: [...editing.steps,
      { stepOrder: next, approverRole: "HR_ADMIN", slaHours: 48, escalationRole: null }] });
  }

  function removeStep(i: number) {
    if (!editing) return;
    const steps = editing.steps.filter((_, idx) => idx !== i)
      .map((s, idx) => ({ ...s, stepOrder: idx + 1 })); // renumber contiguously
    setEditing({ ...editing, steps });
  }

  async function save() {
    if (!editing) return;
    setMsg(null); setError(null);
    try {
      await api.saveWorkflow(editing.id, {
        name: editing.name, active: editing.active, steps: editing.steps,
      });
      setMsg(`Saved "${editing.name}".`);
      setEditing(null);
      await refresh();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Save failed");
    }
  }

  return (
    <div className="stack">
      <section className="card">
        <h2>Approval workflows</h2>
        <p className="muted">Chains are stored as data. Editing steps changes how requests are approved — no redeploy.</p>
        {error && <div className="error">{error}</div>}
        {msg && <div className="ok">{msg}</div>}
        <table className="grid">
          <thead><tr><th>Type</th><th>Name</th><th>Active</th><th>Steps (role · SLA · escalates to)</th><th></th></tr></thead>
          <tbody>
            {workflows.map((w) => (
              <tr key={w.id}>
                <td>{w.requestType}</td>
                <td>{w.name}</td>
                <td>{w.active ? "✓" : "—"}</td>
                <td className="mono">
                  {w.steps.map((s) => `${s.stepOrder}:${s.approverRole}/${s.slaHours}h${s.escalationRole ? "→" + s.escalationRole : ""}`).join("  ·  ")}
                </td>
                <td><button className="secondary" onClick={() => edit(w)}>Edit</button></td>
              </tr>
            ))}
          </tbody>
        </table>
      </section>

      {editing && (
        <section className="card">
          <h2>Edit: {editing.requestType}</h2>
          <label>Name
            <input value={editing.name} onChange={(e) => setEditing({ ...editing, name: e.target.value })} />
          </label>
          <label className="inline">
            <input type="checkbox" checked={editing.active}
              onChange={(e) => setEditing({ ...editing, active: e.target.checked })} /> Active
          </label>

          <h3>Steps</h3>
          <table className="grid">
            <thead><tr><th>#</th><th>Approver role</th><th>SLA (h)</th><th>Escalation role</th><th></th></tr></thead>
            <tbody>
              {editing.steps.map((s, i) => (
                <tr key={i}>
                  <td>{s.stepOrder}</td>
                  <td>
                    <select value={s.approverRole} onChange={(e) => updateStep(i, { approverRole: e.target.value as Role })}>
                      {ROLES.map((r) => <option key={r} value={r}>{r}</option>)}
                    </select>
                  </td>
                  <td>
                    <input type="number" min={1} value={s.slaHours}
                      onChange={(e) => updateStep(i, { slaHours: Number(e.target.value) })} />
                  </td>
                  <td>
                    <select value={s.escalationRole ?? ""}
                      onChange={(e) => updateStep(i, { escalationRole: (e.target.value || null) as Role | null })}>
                      <option value="">(none)</option>
                      {ROLES.map((r) => <option key={r} value={r}>{r}</option>)}
                    </select>
                  </td>
                  <td><button className="reject" onClick={() => removeStep(i)}>Remove</button></td>
                </tr>
              ))}
            </tbody>
          </table>
          <div className="actions">
            <button className="secondary" onClick={addStep}>Add step</button>
            <button className="primary" onClick={save}>Save workflow</button>
            <button className="link" onClick={() => setEditing(null)}>Cancel</button>
          </div>
        </section>
      )}
    </div>
  );
}
