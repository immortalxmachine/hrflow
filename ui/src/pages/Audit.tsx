import { Fragment, useEffect, useState } from "react";
import { api, ApiError } from "../api";
import type { AuditLogDto } from "../types";

/** Read-only, filterable audit log viewer. */
export function Audit() {
  const [rows, setRows] = useState<AuditLogDto[]>([]);
  const [action, setAction] = useState("");
  const [entityType, setEntityType] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [expanded, setExpanded] = useState<number | null>(null);

  async function refresh() {
    setError(null);
    try {
      setRows((await api.audit({ action: action || undefined, entityType: entityType || undefined })).content);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Failed to load audit log");
    }
  }
  useEffect(() => { void refresh(); }, []);

  return (
    <div className="stack">
      <section className="card">
        <div className="card-head">
          <h2>Audit log</h2>
          <div className="filters">
            <input placeholder="action (e.g. REQUEST_STEP_APPROVED)" value={action}
              onChange={(e) => setAction(e.target.value)} />
            <input placeholder="entity type (e.g. request)" value={entityType}
              onChange={(e) => setEntityType(e.target.value)} />
            <button className="secondary" onClick={refresh}>Filter</button>
          </div>
        </div>
        <p className="muted">Append-only. Click a row to see the before/after snapshot.</p>
        {error && <div className="error">{error}</div>}
        <table className="grid">
          <thead><tr><th>#</th><th>When</th><th>Actor</th><th>Action</th><th>Entity</th></tr></thead>
          <tbody>
            {rows.map((a) => (
              <Fragment key={a.id}>
                <tr className="clickable" onClick={() => setExpanded(expanded === a.id ? null : a.id)}>
                  <td>{a.id}</td>
                  <td>{new Date(a.createdAt).toLocaleString()}</td>
                  <td>{a.actorCode}</td>
                  <td><span className="badge">{a.action}</span></td>
                  <td>{a.entityType} #{a.entityId ?? "—"}</td>
                </tr>
                {expanded === a.id && (
                  <tr>
                    <td colSpan={5}>
                      <div className="snapshot">
                        <div><strong>before</strong><pre>{JSON.stringify(a.before, null, 2)}</pre></div>
                        <div><strong>after</strong><pre>{JSON.stringify(a.after, null, 2)}</pre></div>
                      </div>
                    </td>
                  </tr>
                )}
              </Fragment>
            ))}
          </tbody>
        </table>
      </section>
    </div>
  );
}
